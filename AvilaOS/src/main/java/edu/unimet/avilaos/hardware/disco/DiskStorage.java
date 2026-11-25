package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.hardware.disco.enums.DiskBlockType;
import java.util.concurrent.Semaphore;

import static edu.unimet.avilaos.hardware.disco.DiskBlock.END_OF_CHAIN;

/** Mapa de bloques compartido por los archivos y el área de intercambio. */
public final class DiskStorage {

    /** Posiciones de un archivo inicial, en orden de recorrido de su cadena. */
    public record InitialFile(String filePath, int... blockNumbers) {
        public InitialFile {
            validatePath(filePath);
            if (blockNumbers == null || blockNumbers.length == 0) {
                throw new IllegalArgumentException("Faltan los bloques de " + filePath + ".");
            }
            blockNumbers = blockNumbers.clone();
        }

        @Override
        public int[] blockNumbers() {
            return blockNumbers.clone();
        }
    }

    private final DiskBlock[] blocks;
    private final FileReservation[] fileReservations;
    private final Semaphore mutex = new Semaphore(1);

    public static final class FileReservation {
        private final String filePath;
        private final int[] chain;

        private FileReservation(String filePath, int[] chain) {
            this.filePath = filePath;
            this.chain = chain;
        }

        public int[] blockNumbers() { return chain.clone(); }
    }

    DiskStorage(int totalBlocks, int swapSize, InitialFile[] initialFiles) {
        blocks = new DiskBlock[totalBlocks];
        fileReservations = new FileReservation[totalBlocks];
        for (int i = 0; i < blocks.length; i++) blocks[i] = emptyBlock(i, DiskBlockType.FREE);
        if (initialFiles == null) throw new IllegalArgumentException("Faltan los archivos iniciales.");
        for (InitialFile file : initialFiles) {
            if (file == null) throw new IllegalArgumentException("Falta un archivo inicial.");
            requireNewFile(file.filePath());
            int[] chain = file.blockNumbers();
            boolean[] seen = new boolean[blocks.length];
            for (int position : chain) {
                if (position < 0 || position >= blocks.length || seen[position]
                        || blocks[position].contentType() != DiskBlockType.FREE) {
                    throw new IllegalArgumentException("Bloque inválido o repetido en " + file.filePath()
                            + ": " + position);
                }
                seen[position] = true;
            }
            writeFileChain(file.filePath(), chain);
        }
        // La reserva toma bloques libres en orden numérico después de ubicar los archivos iniciales.
        int[] reserved = findAvailableBlocks(DiskBlockType.FREE, swapSize);
        if (reserved.length != swapSize) {
            throw new IllegalArgumentException("Los archivos iniciales no dejan espacio para reservar swap.");
        }
        for (int position : reserved) blocks[position] = emptyBlock(position, DiskBlockType.SWAP_RESERVED);
    }

    public int getTotalBlocks() {
        return blocks.length;
    }

    public DiskBlock[] getBlocks() {
        mutex.acquireUninterruptibly();
        try {
            return blocks.clone();
        } finally {
            mutex.release();
        }
    }

    /** Devuelve la cadena asignada o un arreglo vacío si faltan bloques de archivo. */
    public int[] allocateFile(String filePath, int count) {
        validatePath(filePath);
        validateCount(count);
        mutex.acquireUninterruptibly();
        try {
            requireNewFile(filePath);
            int[] chain = findAvailableBlocks(DiskBlockType.FREE, count);
            writeFileChain(filePath, chain);
            return chain;
        } finally {
            mutex.release();
        }
    }

    /** Reserva posiciones para la E/S sin publicarlas en el mapa de archivos. */
    public FileReservation reserveFile(String filePath, int count) {
        validatePath(filePath);
        validateCount(count);
        mutex.acquireUninterruptibly();
        try {
            requireNewFile(filePath);
            int[] chain = findAvailableBlocks(DiskBlockType.FREE, count);
            if (chain.length == 0) throw new IllegalArgumentException("Espacio insuficiente para " + filePath + ".");
            FileReservation reservation = new FileReservation(filePath, chain);
            for (int block : chain) fileReservations[block] = reservation;
            return reservation;
        } finally {
            mutex.release();
        }
    }

    public void finishFileReservation(FileReservation reservation, boolean commit) {
        mutex.acquireUninterruptibly();
        try {
            int first = reservation.chain[0];
            if (first >= blocks.length || fileReservations[first] != reservation) {
                if (!commit) return;
                throw new IllegalArgumentException("La reserva de " + reservation.filePath + " ya no está activa.");
            }
            if (commit) writeFileChain(reservation.filePath, reservation.chain);
            for (int block : reservation.chain) fileReservations[block] = null;
        } finally {
            mutex.release();
        }
    }

    public int[] getFileChain(String filePath, int firstBlock) {
        validatePath(filePath);
        mutex.acquireUninterruptibly();
        try {
            return readFileChain(filePath, firstBlock);
        } finally {
            mutex.release();
        }
    }

    public void releaseFile(String filePath, int firstBlock) {
        validatePath(filePath);
        mutex.acquireUninterruptibly();
        try {
            for (int position : readFileChain(filePath, firstBlock)) {
                blocks[position] = emptyBlock(position, DiskBlockType.FREE);
            }
        } finally {
            mutex.release();
        }
    }

    /** Cambia la ruta guardada en los bloques de un archivo, o de todos los archivos bajo un directorio. */
    public void renamePath(String oldPath, String newPath) {
        validatePath(oldPath);
        validatePath(newPath);
        String oldPrefix = oldPath + "/";
        mutex.acquireUninterruptibly();
        try {
            for (FileReservation reservation : fileReservations) {
                if (reservation != null && (reservation.filePath.equals(oldPath)
                        || reservation.filePath.startsWith(oldPrefix))) {
                    throw new IllegalArgumentException("Hay una creación pendiente dentro de " + oldPath + ".");
                }
            }
            for (int i = 0; i < blocks.length; i++) {
                String path = blocks[i].filePath();
                if (path == null) continue;
                String renamed;
                if (path.equals(oldPath)) renamed = newPath;
                else if (path.startsWith(oldPrefix)) renamed = newPath + path.substring(oldPath.length());
                else continue;
                blocks[i] = new DiskBlock(i, blocks[i].contentType(), renamed, blocks[i].processId(),
                        blocks[i].nextBlock());
            }
        } finally {
            mutex.release();
        }
    }

    /** Devuelve las posiciones del PCB o un arreglo vacío si el área de swap no alcanza. */
    public int[] allocateSwap(int processId, int count) {
        validateProcessId(processId);
        validateCount(count);
        mutex.acquireUninterruptibly();
        try {
            for (DiskBlock block : blocks) {
                if (block.processId() == processId) {
                    throw new IllegalArgumentException("El PCB " + processId + " ya ocupa bloques de swap.");
                }
            }
            int[] allocated = findAvailableBlocks(DiskBlockType.SWAP_RESERVED, count);
            for (int position : allocated) {
                blocks[position] = new DiskBlock(position, DiskBlockType.SWAP_OCCUPIED, null,
                        processId, END_OF_CHAIN);
            }
            return allocated;
        } finally {
            mutex.release();
        }
    }

    public void releaseSwap(int processId) {
        validateProcessId(processId);
        mutex.acquireUninterruptibly();
        try {
            for (int i = 0; i < blocks.length; i++) {
                if (blocks[i].processId() == processId) blocks[i] = emptyBlock(i, DiskBlockType.SWAP_RESERVED);
            }
        } finally {
            mutex.release();
        }
    }

    /** Posiciones de swap que ocupa el PCB, en orden ascendente; vacio si no esta en swap. */
    public int[] getSwapBlocks(int processId) {
        validateProcessId(processId);
        mutex.acquireUninterruptibly();
        try {
            int count = 0;
            for (DiskBlock block : blocks) {
                if (block.contentType() == DiskBlockType.SWAP_OCCUPIED && block.processId() == processId) count++;
            }
            int[] positions = new int[count];
            int found = 0;
            for (int i = 0; i < blocks.length; i++) {
                if (blocks[i].contentType() == DiskBlockType.SWAP_OCCUPIED && blocks[i].processId() == processId) {
                    positions[found++] = i;
                }
            }
            return positions;
        } finally {
            mutex.release();
        }
    }

    private int[] findAvailableBlocks(DiskBlockType type, int count) {
        if (count > blocks.length) return new int[0];
        int[] positions = new int[count];
        int found = 0;
        for (int i = 0; i < blocks.length && found < count; i++) {
            if (blocks[i].contentType() == type && fileReservations[i] == null) positions[found++] = i;
        }
        return found == count ? positions : new int[0];
    }

    private void writeFileChain(String filePath, int[] chain) {
        for (int i = 0; i < chain.length; i++) {
            int position = chain[i];
            int next = i + 1 < chain.length ? chain[i + 1] : END_OF_CHAIN;
            blocks[position] = new DiskBlock(position, DiskBlockType.FILE, filePath, -1, next);
        }
    }

    private int[] readFileChain(String filePath, int firstBlock) {
        int count = countFileBlocks(filePath);
        if (count == 0) throw new IllegalArgumentException("Archivo sin bloques: " + filePath);
        int[] chain = new int[count];
        int position = firstBlock;
        for (int i = 0; i < count; i++) {
            if (position < 0 || position >= blocks.length || !filePath.equals(blocks[position].filePath())) {
                throw new IllegalArgumentException("Cadena inválida de " + filePath + ".");
            }
            chain[i] = position;
            position = blocks[position].nextBlock();
        }
        if (position != END_OF_CHAIN) {
            throw new IllegalArgumentException("Cadena sin fin en " + filePath + ".");
        }
        return chain;
    }

    private int countFileBlocks(String filePath) {
        int count = 0;
        for (DiskBlock block : blocks) {
            if (filePath.equals(block.filePath())) count++;
        }
        return count;
    }

    private void requireNewFile(String filePath) {
        if (countFileBlocks(filePath) != 0) {
            throw new IllegalArgumentException("El archivo ya tiene bloques: " + filePath);
        }
        for (FileReservation reservation : fileReservations) {
            if (reservation != null && reservation.filePath.equals(filePath)) {
                throw new IllegalArgumentException("Ya hay una creación pendiente para " + filePath + ".");
            }
        }
    }

    private static DiskBlock emptyBlock(int position, DiskBlockType type) {
        return new DiskBlock(position, type, null, -1, END_OF_CHAIN);
    }

    private static void validatePath(String filePath) {
        if (filePath == null || filePath.isBlank()) throw new IllegalArgumentException("Falta la ruta del archivo.");
    }

    private static void validateCount(int count) {
        if (count < 1) throw new IllegalArgumentException("La cantidad de bloques debe ser positiva.");
    }

    private static void validateProcessId(int processId) {
        if (processId < 1) throw new IllegalArgumentException("El ID del PCB debe ser positivo.");
    }
}

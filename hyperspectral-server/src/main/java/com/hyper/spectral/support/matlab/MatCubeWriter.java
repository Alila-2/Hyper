package com.hyper.spectral.support.matlab;

import com.hyper.spectral.support.envi.EnviCubeReader.EnviCube;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writes a simple MATLAB v5 MAT file for one real numeric 3D cube variable.
 */
public final class MatCubeWriter {

    private static final int MI_INT8 = 1;
    private static final int MI_UINT8 = 2;
    private static final int MI_INT16 = 3;
    private static final int MI_UINT16 = 4;
    private static final int MI_INT32 = 5;
    private static final int MI_UINT32 = 6;
    private static final int MI_SINGLE = 7;
    private static final int MI_DOUBLE = 9;
    private static final int MI_MATRIX = 14;

    private static final int MX_DOUBLE_CLASS = 6;
    private static final int MX_SINGLE_CLASS = 7;
    private static final int MX_UINT8_CLASS = 9;
    private static final int MX_INT16_CLASS = 10;
    private static final int MX_UINT16_CLASS = 11;
    private static final int MX_INT32_CLASS = 12;
    private static final int MX_UINT32_CLASS = 13;

    private static final DateTimeFormatter HEADER_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.ROOT);

    private MatCubeWriter() {
    }

    public static void write(Path targetPath, String variableName, EnviCube cube) throws IOException {
        int miType = matlabDataType(cube.getDataType());
        int mxClass = matlabClassType(cube.getDataType());
        int bytesPerElement = bytesPerElement(miType);
        long elementCount = (long) cube.getLines() * cube.getSamples() * cube.getBands();
        long dataBytes = elementCount * bytesPerElement;
        if (dataBytes > Integer.MAX_VALUE) {
            throw new IOException("MAT 数据体积超过单文件实现上限: " + dataBytes + " bytes");
        }

        byte[] nameBytes = variableName.getBytes(StandardCharsets.UTF_8);
        int dimensionsBytes = 3 * Integer.BYTES;
        int matrixBytes = elementSize(8)
                + elementSize(dimensionsBytes)
                + elementSize(nameBytes.length)
                + elementSize((int) dataBytes);

        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(targetPath))) {
            writeHeader(outputStream);
            writeInt(outputStream, MI_MATRIX);
            writeInt(outputStream, matrixBytes);

            writeElementTag(outputStream, MI_UINT32, 8);
            writeInt(outputStream, mxClass);
            writeInt(outputStream, 0);

            writeElementTag(outputStream, MI_INT32, dimensionsBytes);
            writeInt(outputStream, cube.getLines());
            writeInt(outputStream, cube.getSamples());
            writeInt(outputStream, cube.getBands());
            writePadding(outputStream, dimensionsBytes);

            writeElementTag(outputStream, MI_INT8, nameBytes.length);
            outputStream.write(nameBytes);
            writePadding(outputStream, nameBytes.length);

            writeElementTag(outputStream, miType, (int) dataBytes);
            writeCubeData(outputStream, cube, miType);
            writePadding(outputStream, (int) dataBytes);
        }
    }

    private static void writeHeader(OutputStream outputStream) throws IOException {
        byte[] description = new byte[116];
        byte[] text = String.format(
                Locale.ROOT,
                "MATLAB 5.0 MAT-file, Platform: Java, Created on: %s",
                LocalDateTime.now().format(HEADER_TIME_FORMATTER)
        ).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text, 0, description, 0, Math.min(text.length, description.length));
        outputStream.write(description);
        outputStream.write(new byte[8]);
        outputStream.write(new byte[]{0, 1, 'I', 'M'});
    }

    private static void writeCubeData(OutputStream outputStream, EnviCube cube, int miType) throws IOException {
        for (int band = 0; band < cube.getBands(); band++) {
            for (int x = 0; x < cube.getSamples(); x++) {
                for (int y = 0; y < cube.getLines(); y++) {
                    float value = cube.getValue(x, y, band);
                    switch (miType) {
                        case MI_UINT8:
                            outputStream.write(clampUnsignedByte(Math.round(value)));
                            break;
                        case MI_INT16:
                            writeShort(outputStream, clampSignedShort(Math.round(value)));
                            break;
                        case MI_UINT16:
                            writeShort(outputStream, clampUnsignedShort(Math.round(value)));
                            break;
                        case MI_INT32:
                            writeInt(outputStream, Math.round(value));
                            break;
                        case MI_UINT32:
                            writeUnsignedInt(outputStream, clampUnsignedInt(Math.round(value)));
                            break;
                        case MI_SINGLE:
                            writeInt(outputStream, Float.floatToRawIntBits(value));
                            break;
                        case MI_DOUBLE:
                            writeLong(outputStream, Double.doubleToRawLongBits(value));
                            break;
                        default:
                            throw new IOException("不支持的 MAT 数据类型: " + miType);
                    }
                }
            }
        }
    }

    private static void writeElementTag(OutputStream outputStream, int dataType, int numBytes) throws IOException {
        writeInt(outputStream, dataType);
        writeInt(outputStream, numBytes);
    }

    private static void writePadding(OutputStream outputStream, int dataBytes) throws IOException {
        int padding = (8 - (dataBytes % 8)) % 8;
        for (int index = 0; index < padding; index++) {
            outputStream.write(0);
        }
    }

    private static int elementSize(int dataBytes) {
        return 8 + dataBytes + ((8 - (dataBytes % 8)) % 8);
    }

    private static int matlabDataType(int enviType) throws IOException {
        switch (enviType) {
            case 1:
                return MI_UINT8;
            case 2:
                return MI_INT16;
            case 3:
                return MI_INT32;
            case 4:
                return MI_SINGLE;
            case 5:
                return MI_DOUBLE;
            case 12:
                return MI_UINT16;
            case 13:
                return MI_UINT32;
            default:
                throw new IOException("暂不支持写出该 ENVI 数据类型的 MAT 文件: " + enviType);
        }
    }

    private static int matlabClassType(int enviType) throws IOException {
        switch (enviType) {
            case 1:
                return MX_UINT8_CLASS;
            case 2:
                return MX_INT16_CLASS;
            case 3:
                return MX_INT32_CLASS;
            case 4:
                return MX_SINGLE_CLASS;
            case 5:
                return MX_DOUBLE_CLASS;
            case 12:
                return MX_UINT16_CLASS;
            case 13:
                return MX_UINT32_CLASS;
            default:
                throw new IOException("暂不支持写出该 ENVI 数据类型的 MAT 文件: " + enviType);
        }
    }

    private static int bytesPerElement(int miType) throws IOException {
        switch (miType) {
            case MI_INT8:
            case MI_UINT8:
                return 1;
            case MI_INT16:
            case MI_UINT16:
                return 2;
            case MI_INT32:
            case MI_UINT32:
            case MI_SINGLE:
                return 4;
            case MI_DOUBLE:
                return 8;
            default:
                throw new IOException("未知 MAT 数据元素类型: " + miType);
        }
    }

    private static void writeShort(OutputStream outputStream, int value) throws IOException {
        outputStream.write(value & 0xFF);
        outputStream.write((value >>> 8) & 0xFF);
    }

    private static void writeInt(OutputStream outputStream, int value) throws IOException {
        outputStream.write(value & 0xFF);
        outputStream.write((value >>> 8) & 0xFF);
        outputStream.write((value >>> 16) & 0xFF);
        outputStream.write((value >>> 24) & 0xFF);
    }

    private static void writeUnsignedInt(OutputStream outputStream, long value) throws IOException {
        outputStream.write((int) (value & 0xFF));
        outputStream.write((int) ((value >>> 8) & 0xFF));
        outputStream.write((int) ((value >>> 16) & 0xFF));
        outputStream.write((int) ((value >>> 24) & 0xFF));
    }

    private static void writeLong(OutputStream outputStream, long value) throws IOException {
        outputStream.write((int) (value & 0xFF));
        outputStream.write((int) ((value >>> 8) & 0xFF));
        outputStream.write((int) ((value >>> 16) & 0xFF));
        outputStream.write((int) ((value >>> 24) & 0xFF));
        outputStream.write((int) ((value >>> 32) & 0xFF));
        outputStream.write((int) ((value >>> 40) & 0xFF));
        outputStream.write((int) ((value >>> 48) & 0xFF));
        outputStream.write((int) ((value >>> 56) & 0xFF));
    }

    private static int clampUnsignedByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int clampSignedShort(int value) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    private static int clampUnsignedShort(int value) {
        return Math.max(0, Math.min(0xFFFF, value));
    }

    private static long clampUnsignedInt(int value) {
        return Math.max(0L, Math.min(0xFFFFFFFFL, value));
    }
}

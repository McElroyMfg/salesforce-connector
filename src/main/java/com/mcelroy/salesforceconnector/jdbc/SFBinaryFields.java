// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import openize.heic.decoder.HeicImage;
import openize.heic.decoder.PixelFormat;
import openize.io.IOSeekMode;
import openize.io.IOStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SFBinaryFields {
    private SFBinaryFields() {
    }

    static byte[] read(InputStream input) throws SQLException {
        return readStream(input, -1);
    }

    static byte[] read(InputStream input, long length) throws SQLException {
        if (length < 0)
            throw new SQLException("Binary stream length cannot be negative");
        return readStream(input, length);
    }

    private static byte[] readStream(InputStream input, long length) throws SQLException {
        if (input == null)
            return null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long remaining = length;
        try {
            while (length < 0 || remaining > 0) {
                int count = input.read(buffer, 0,
                        length < 0 ? buffer.length : (int) Math.min(buffer.length, remaining));
                if (count < 0) {
                    if (length >= 0)
                        throw new SQLException("Binary stream is shorter than its declared length");
                    break;
                }
                if (count == 0) {
                    int value = input.read();
                    if (value < 0) {
                        if (length >= 0)
                            throw new SQLException("Binary stream is shorter than its declared length");
                        break;
                    }
                    output.write(value);
                    remaining--;
                } else {
                    output.write(buffer, 0, count);
                    remaining -= count;
                }
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new SQLException("Could not read binary stream", e);
        }
    }

    static byte[] read(Blob blob) throws SQLException {
        if (blob == null)
            return null;
        try (InputStream input = blob.getBinaryStream()) {
            return read(input, blob.length());
        } catch (IOException e) {
            throw new SQLException("Could not close Blob stream", e);
        }
    }

    static Map<String, Object> prepare(SFConnection connection, SFClientConnection client,
                                       String table, Map<String, Object> values) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(values);
        if (row.values().stream().noneMatch(value -> value instanceof byte[]))
            return row;
        Set<String> binaryFields = new HashSet<>();
        try {
            JSONObject description = connection.getMetadataCache()
                    .get("sobject:" + table, () -> client.describe(table));
            JSONArray fields = description.getJSONArray("fields");
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                if ("base64".equalsIgnoreCase(field.optString("type")))
                    binaryFields.add(field.getString("name").toLowerCase(Locale.ROOT));
            }
        } catch (RuntimeException e) {
            throw new SQLException("Could not describe binary fields for " + table + ": " + e.getMessage(), e);
        }
        boolean converted = false;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (!(entry.getValue() instanceof byte[]))
                continue;
            if (!binaryFields.contains(entry.getKey().toLowerCase(Locale.ROOT)))
                throw new SQLException("Binary data requires a base64 field: " + entry.getKey());
            byte[] bytes = (byte[]) entry.getValue();
            if (connection.getConvertHeic() && isHeic(bytes)) {
                bytes = toJpeg(bytes);
                converted = true;
            }
            entry.setValue(Base64.getEncoder().encodeToString(bytes));
        }
        if (converted) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if (("PathOnClient".equalsIgnoreCase(key) || "Title".equalsIgnoreCase(key)
                        || "Name".equalsIgnoreCase(key)) && entry.getValue() instanceof String)
                    entry.setValue(((String) entry.getValue()).replaceFirst("(?i)\\.hei[cf]$", ".jpg"));
            }
        }
        return row;
    }

    static boolean isHeic(byte[] bytes) {
        if (bytes == null || bytes.length < 16
                || !"ftyp".equals(new String(bytes, 4, 4, StandardCharsets.US_ASCII)))
            return false;
        long size = ((bytes[0] & 255L) << 24) | ((bytes[1] & 255L) << 16)
                | ((bytes[2] & 255L) << 8) | (bytes[3] & 255L);
        if (size != 0 && (size < 16 || size > bytes.length))
            return false;
        int end = size == 0 ? bytes.length : (int) size;
        boolean hevc = false;
        boolean heif = false;
        boolean avif = false;
        for (int offset = 8; offset + 4 <= end; offset += 4) {
            if (offset == 12)
                continue; // minor version, not a compatible brand
            String brand = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            if ("heic".equals(brand) || "heix".equals(brand) || "hevc".equals(brand)
                    || "hevx".equals(brand))
                hevc = true;
            if ("mif1".equals(brand) || "msf1".equals(brand) || "heif".equals(brand))
                heif = true;
            if ("avif".equals(brand) || "avis".equals(brand))
                avif = true;
        }
        return hevc || (heif && !avif);
    }

    private static byte[] toJpeg(byte[] bytes) throws SQLException {
        try (IOStream input = new MemoryStream(bytes)) {
            HeicImage image = HeicImage.load(input);
            long width = image.getWidth();
            long height = image.getHeight();
            if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE
                    || height > Integer.MAX_VALUE || width * height > Integer.MAX_VALUE)
                throw new IOException("Invalid HEIC image dimensions");
            // Openize applies HEIF irot/imir when extracting pixels.
            // TODO: apply EXIF-only orientation when no HEIF transform is present.
            int[] pixels = image.getInt32Array(PixelFormat.Argb32);
            if (pixels == null || pixels.length != width * height)
                throw new IOException("Invalid HEIC pixel data");
            BufferedImage jpeg = new BufferedImage((int) width, (int) height, BufferedImage.TYPE_INT_RGB);
            jpeg.setRGB(0, 0, (int) width, (int) height, pixels, 0, (int) width);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(jpeg, "JPEG", output))
                throw new IOException("JPEG writer is unavailable");
            return output.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new SQLException("Could not convert HEIC to JPEG: " + e.getMessage(), e);
        }
    }

    private static final class MemoryStream implements IOStream {
        private byte[] data;
        private long position;

        private MemoryStream(byte[] data) {
            this.data = data;
        }

        private void checkOpen() {
            if (data == null)
                throw new IllegalStateException("HEIC stream is closed");
        }

        @Override
        public int read(byte[] target) {
            return read(target, 0, target.length);
        }

        @Override
        public int read(byte[] target, int offset, int count) {
            checkOpen();
            if (offset < 0 || count < 0 || offset > target.length - count)
                throw new IndexOutOfBoundsException();
            if (count == 0)
                return 0;
            if (position >= data.length)
                return -1;
            int length = (int) Math.min(count, data.length - position);
            System.arraycopy(data, (int) position, target, offset, length);
            position += length;
            return length;
        }

        @Override
        public long setPosition(long value) {
            checkOpen();
            if (value < 0)
                throw new IllegalArgumentException("Negative HEIC stream position");
            long previous = position;
            position = value;
            return previous;
        }

        @Override
        public long getPosition() {
            checkOpen();
            return position;
        }

        @Override
        public void seek(long offset, IOSeekMode mode) {
            long start = mode == IOSeekMode.BEGIN ? 0
                    : mode == IOSeekMode.CURRENT ? getPosition() : getLength();
            setPosition(Math.addExact(start, offset));
        }

        @Override
        public long getLength() {
            checkOpen();
            return data.length;
        }

        @Override public void write(byte[] bytes) { throw new UnsupportedOperationException("Read-only HEIC stream"); }
        @Override public void write(byte[] bytes, int offset, int count) { write(bytes); }
        @Override public void setLength(long length) { throw new UnsupportedOperationException("Read-only HEIC stream"); }
        @Override public void close() { data = null; }
    }
}

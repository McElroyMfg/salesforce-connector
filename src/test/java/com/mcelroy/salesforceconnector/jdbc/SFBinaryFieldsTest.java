// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SFBinaryFieldsTest {
    /*
     * synthetic-checkerboard.heic is an original, MIT-licensed test fixture, not
     * a downloaded photograph. Its 128x128 RGB pixels alternate (230,40,60)
     * and (20,180,220) in 8x8 squares. Each channel has deterministic noise
     * from Python random.Random(20261007).randrange(-40,41), clamped to 0..255.
     * A PNG was generated with Python 3.12.3's standard-library struct/zlib
     * and encoded using libheif heif-enc 1.17.6 with x265 3.5+1-f0c1022b6:
     * -q 90 -p x265:pools=1 -p x265:frame-threads=1.
     * heif-info and heif-convert 1.17.6 verified the dimensions and decoding.
     */
    private static final String FIXTURE = "synthetic-checkerboard.heic";
    private static final String[] HEIC_BRANDS = {
            "heic", "heix", "hevc", "hevx", "mif1", "msf1", "heif"
    };

    private final SFConnection connection = new SFConnection(null);
    private final SFClientConnection client = mock(SFClientConnection.class);

    @Test
    public void detectsEveryHeicMajorBrand() {
        for (String brand : HEIC_BRANDS)
            assertTrue(brand, SFBinaryFields.isHeic(ftyp(brand, "0000")));
    }

    @Test
    public void detectsEveryHeicCompatibleBrand() {
        for (String brand : HEIC_BRANDS)
            assertTrue(brand, SFBinaryFields.isHeic(ftyp("isom", "0000", "isom", brand)));
    }

    @Test
    public void avifWithGenericHeifBrandsIsNotHeic() {
        for (String major : new String[]{"avif", "avis"}) {
            for (String compatible : new String[]{"mif1", "msf1", "heif"})
                assertFalse(major + "/" + compatible,
                        SFBinaryFields.isHeic(ftyp(major, "0000", compatible)));
        }
        assertFalse(SFBinaryFields.isHeic(ftyp("mif1", "0000", "avif")));
    }

    @Test
    public void hevcSpecificBrandsRemainHeicEvenAlongsideAvifBrands() {
        assertTrue(SFBinaryFields.isHeic(ftyp("heic", "0000", "avif", "mif1")));
        assertTrue(SFBinaryFields.isHeic(ftyp("avif", "0000", "heic", "mif1")));
    }

    @Test
    public void avifWithGenericHeifBrandsPreservesBytesAndFilenames() throws Exception {
        describeBinaryFields();
        for (String major : new String[]{"avif", "avis"}) {
            for (String compatible : new String[]{"mif1", "msf1", "heif"}) {
                byte[] bytes = ftyp(major, "0000", compatible);
                Map<String, Object> input = row(bytes);
                Map<String, Object> output = prepare(input);
                assertArrayEquals(bytes, Base64.getDecoder().decode((String) output.get("VersionData")));
                assertNamesUnchanged(input, output);
            }
        }
        byte[] bytes = ftyp("mif1", "0000", "avif");
        Map<String, Object> input = row(bytes);
        Map<String, Object> output = prepare(input);
        assertArrayEquals(bytes, Base64.getDecoder().decode((String) output.get("VersionData")));
        assertNamesUnchanged(input, output);
        verify(client, times(1)).describe("ContentVersion");
    }

    @Test
    public void doesNotTreatMinorVersionOrTrailingBoxAsCompatibleBrand() {
        for (String brand : HEIC_BRANDS) {
            assertFalse(brand, SFBinaryFields.isHeic(ftyp("isom", brand, "avif")));
            byte[] header = ftyp("isom", "0000");
            byte[] trailing = Arrays.copyOf(header, header.length + 4);
            System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, trailing, header.length, 4);
            assertFalse(brand, SFBinaryFields.isHeic(trailing));
        }
    }

    @Test
    public void rejectsNullTruncatedAndInvalidFtypBoxes() {
        assertFalse(SFBinaryFields.isHeic(null));
        byte[] valid = ftyp("heic", "0000", "mif1");
        for (int length = 0; length < valid.length; length++)
            assertFalse("Truncated length " + length, SFBinaryFields.isHeic(Arrays.copyOf(valid, length)));
        byte[] tooSmall = valid.clone();
        ByteBuffer.wrap(tooSmall).putInt(12);
        assertFalse(SFBinaryFields.isHeic(tooSmall));
        byte[] wrongBox = valid.clone();
        wrongBox[4] = 'm';
        assertFalse(SFBinaryFields.isHeic(wrongBox));
        assertFalse(SFBinaryFields.isHeic(ftyp("avif", "0000", "avis")));
    }

    @Test
    public void jpegAndPngAreNotHeic() throws Exception {
        for (String format : new String[]{"jpeg", "png"})
            assertFalse(format, SFBinaryFields.isHeic(image(format)));
    }

    @Test
    public void syntheticFixtureIsHeic() throws Exception {
        assertTrue(SFBinaryFields.isHeic(fixture()));
    }

    @Test
    public void connectionEnablesConversionByDefaultAndCanDisableIt() {
        assertTrue(connection.getConvertHeic());
        connection.setConvertHeic(false);
        assertFalse(connection.getConvertHeic());
        connection.setConvertHeic(true);
        assertTrue(connection.getConvertHeic());
    }

    @Test
    public void disabledConversionPreservesHeicBytesAndAllNames() throws Exception {
        describeBinaryFields();
        connection.setConvertHeic(false);
        byte[] bytes = fixture();
        Map<String, Object> input = row(bytes);
        Map<String, Object> output = prepare(input);
        assertArrayEquals(bytes, Base64.getDecoder().decode((String) output.get("VersionData")));
        assertNamesUnchanged(input, output);
        assertSame(bytes, input.get("VersionData"));
        assertNotSame(input, output);
    }

    @Test
    public void nonHeicBytesAreBase64EncodedWithoutRenaming() throws Exception {
        describeBinaryFields();
        for (byte[] bytes : new byte[][]{new byte[0], {0, 1, (byte) 255}, image("jpeg"), image("png")}) {
            Map<String, Object> input = row(bytes);
            Map<String, Object> output = prepare(input);
            assertEquals(Base64.getEncoder().encodeToString(bytes), output.get("VersionData"));
            assertNamesUnchanged(input, output);
            assertSame(bytes, input.get("VersionData"));
        }
        verify(client, times(1)).describe("ContentVersion");
    }

    @Test
    public void nonbinaryValuesAndAlreadyEncodedHeicDoNotDescribeOrConvert() throws Exception {
        Map<String, Object> input = row(Base64.getEncoder().encodeToString(fixture()));
        input.put("Description", null);
        input.put("Count", 7);
        assertEquals(input, prepare(input));
        assertEquals(new LinkedHashMap<String, Object>(), prepare(new LinkedHashMap<String, Object>()));
        verifyZeroInteractions(client);
    }

    @Test
    public void binaryValuesRequireBase64Metadata() throws Exception {
        describeBinaryFields();
        Map<String, Object> input = new LinkedHashMap<>();
        byte[] bytes = new byte[]{1, 2, 3};
        input.put("Title", bytes);
        try {
            prepare(input);
            fail("Expected binary binding on a string field to fail");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage().contains("base64"));
            assertTrue(expected.getMessage().contains("Title"));
        }
        assertSame(bytes, input.get("Title"));
    }

    @Test
    public void describeMetadataIsReusedAndFieldNamesAreCaseInsensitive() throws Exception {
        describeBinaryFields();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("versiondata", new byte[]{1, 2, 3});
            assertEquals("AQID", prepare(input).get("versiondata"));
        }
        verify(client, times(1)).describe("ContentVersion");
    }

    @Test
    public void heicConvertsToDecodableJpegAndRenamesOnlyFilenameFields() throws Exception {
        describeBinaryFields();
        byte[] original = fixture();
        byte[] originalCopy = original.clone();
        Map<String, Object> input = row(original);
        input.put("Description", "keep.HEIC");
        Map<String, Object> output = prepare(input);
        byte[] jpeg = Base64.getDecoder().decode((String) output.get("VersionData"));
        assertEquals(0xff, jpeg[0] & 0xff);
        assertEquals(0xd8, jpeg[1] & 0xff);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertNotNull("Converted JPEG must decode with the Java ImageIO reader", decoded);
        assertEquals(128, decoded.getWidth());
        assertEquals(128, decoded.getHeight());
        assertEquals("folder/photo.jpg", output.get("PathOnClient"));
        assertEquals("photo.jpg", output.get("Title"));
        assertEquals("display.jpg", output.get("Name"));
        assertEquals("keep.HEIC", output.get("Description"));
        assertArrayEquals(originalCopy, original);
        assertNamesUnchanged(row(original), input);
    }

    @Test
    public void convertedImagesRenameMixedCaseKeysButNotUnrelatedSuffixes() throws Exception {
        describeBinaryFields();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("VersionData", fixture());
        input.put("pathonclient", "folder/photo.HeIf");
        input.put("TITLE", "photo.heic.backup");
        input.put("nAmE", "extensionless");
        Map<String, Object> output = prepare(input);
        assertEquals("folder/photo.jpg", output.get("pathonclient"));
        assertEquals("photo.heic.backup", output.get("TITLE"));
        assertEquals("extensionless", output.get("nAmE"));
    }

    private void describeBinaryFields() {
        when(client.describe("ContentVersion")).thenReturn(new JSONObject().put("fields", new JSONArray()
                .put(new JSONObject().put("name", "VersionData").put("type", "base64"))
                .put(new JSONObject().put("name", "Title").put("type", "string"))));
    }

    private Map<String, Object> prepare(Map<String, Object> input) throws SQLException {
        return SFBinaryFields.prepare(connection, client, "ContentVersion", input);
    }

    private static Map<String, Object> row(Object binary) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("VersionData", binary);
        input.put("PathOnClient", "folder/photo.HEIC");
        input.put("Title", "photo.heif");
        input.put("Name", "display.HeIc");
        return input;
    }

    private static void assertNamesUnchanged(Map<String, Object> input, Map<String, Object> output) {
        for (String name : new String[]{"PathOnClient", "Title", "Name"})
            assertEquals(name, input.get(name), output.get(name));
    }

    private static byte[] ftyp(String major, String minor, String... compatible) {
        ByteBuffer box = ByteBuffer.allocate(16 + 4 * compatible.length);
        box.putInt(box.capacity());
        box.put("ftyp".getBytes(StandardCharsets.US_ASCII));
        box.put(major.getBytes(StandardCharsets.US_ASCII));
        box.put(minor.getBytes(StandardCharsets.US_ASCII));
        for (String brand : compatible)
            box.put(brand.getBytes(StandardCharsets.US_ASCII));
        return box.array();
    }

    private static byte[] image(String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), format, output));
        return output.toByteArray();
    }

    private static byte[] fixture() throws Exception {
        try (InputStream input = SFBinaryFieldsTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull("Synthetic HEIC fixture must be on the test classpath", input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            for (int length; (length = input.read(buffer)) != -1;)
                output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }
}

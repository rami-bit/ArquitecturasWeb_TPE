package com.ticketya.ticket.application;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeRendererTest {
    @Test
    void rendersPngThatDecodesBackToTheSignedPayload() throws Exception {
        String payload = "v1.ticket-id.signature";
        String encodedPng = new QrCodeRenderer().renderPngBase64(payload);
        byte[] png = Base64.getDecoder().decode(encodedPng);
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        assertThat(new MultiFormatReader().decode(bitmap).getText()).isEqualTo(payload);
    }
}

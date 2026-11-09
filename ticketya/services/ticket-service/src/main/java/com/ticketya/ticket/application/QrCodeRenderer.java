package com.ticketya.ticket.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Component
public class QrCodeRenderer {
    private static final int IMAGE_SIZE = 320;

    public String renderPngBase64(String payload) {
        try {
            var matrix = new QRCodeWriter().encode(
                    payload, BarcodeFormat.QR_CODE, IMAGE_SIZE, IMAGE_SIZE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Unable to render ticket QR code", exception);
        }
    }
}

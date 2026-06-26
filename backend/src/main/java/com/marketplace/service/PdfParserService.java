package com.marketplace.service;

import com.marketplace.dto.OperationDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfParserService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public OperationDto parse(InputStream pdfStream, String filename) throws IOException {
        byte[] bytes = pdfStream.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return extractFields(text, filename);
        }
    }

    private OperationDto extractFields(String text, String filename) {
        OperationDto dto = new OperationDto();

        // Número de factura. El PDF viene en dos columnas, así que al sacar el texto
        // las etiquetas quedan juntas por un lado y los valores por otro. Por eso el
        // número NO queda pegado a la etiqueta "FACTURA:". El formato real es
        // 0005-00000717 (4-5 dígitos, guion, 6-8 dígitos).
        String invoiceNum = extract(text, "FACTURA:\\s*(\\d{3,5}-\\d{6,8})", 1);
        if (invoiceNum == null) {
            invoiceNum = extract(text, "(\\d{3,5}-\\d{6,8})", 1); // primer número con ese formato
        }
        dto.setInvoiceNumber(invoiceNum);

        dto.setPaymentMethod(extract(text, "(?i)M[eé]todo de pago:\\s*(.+)", 1));
        dto.setBuyerName(extract(text, "Nombre:\\s*(.+)", 1));
        dto.setBuyerDoc(extract(text, "CUIT/DNI/CUIL:\\s*(\\d+)", 1));
        dto.setBuyerProvince(extractProvince(text));

        // Fecha de emisión. Por el mismo tema de las dos columnas, la fecha tampoco
        // queda pegada a su etiqueta (en el medio se cuela el número de factura). La
        // fecha de emisión es SIEMPRE la primera fecha dd/MM/yyyy del documento
        // (después vienen vencimiento, inicio de actividad, etc).
        String dateStr = extract(text, "Fecha de Emisi[oó]n:\\s*(\\d{2}/\\d{2}/\\d{4})", 1);
        if (dateStr == null) {
            dateStr = extract(text, "(\\d{2}/\\d{2}/\\d{4})", 1); // primera fecha del documento
        }
        if (dateStr != null) {
            try {
                dto.setInvoiceDate(LocalDate.parse(dateStr, DATE_FMT));
            } catch (Exception ignored) {
                // si el formato no es el esperado, dejo la fecha en null
            }
        }

        // ---------------------------------------------------------------
        // Busco la sección de productos:
        // arranco después del encabezado de la tabla (la línea que tiene "SKU" y
        // "DESCRIPCI") y voy juntando hasta 6 líneas, hasta toparme con una línea
        // de totales o de envío. Después intento sacar el nombre y el precio de
        // esas líneas juntas (porque a veces el nombre ocupa varias líneas).
        // ---------------------------------------------------------------
        String[] lines = text.split("\\n");
        List<String> productSection = new ArrayList<>();
        boolean headerFound = false;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!headerFound) {
                if (trimmed.toUpperCase().contains("SKU") && trimmed.toUpperCase().contains("DESCRIPCI")) {
                    headerFound = true;
                }
                continue;
            }
            // Corto al llegar a las líneas de totales / envío
            if (trimmed.startsWith("Costo") || trimmed.startsWith("Importe")
                    || trimmed.toUpperCase().startsWith("TOTAL")) {
                break;
            }
            if (!trimmed.isBlank()) {
                productSection.add(trimmed);
                if (productSection.size() >= 6) break;
            }
        }

        if (!productSection.isEmpty()) {
            parseProductSection(productSection, dto, text);
        }

        // Costo de envío
        String shipping = extract(text, "Costo Envi[oó].*?([\\d]+[.,][\\d]{2})", 1);
        dto.setShippingCost(shipping != null ? parseMoney(shipping) : BigDecimal.ZERO);

        return dto;
    }

    /**
     * Intenta sacar nombre, cantidad y precio unitario de un bloque de líneas
     * que corresponde a la sección de ítems de la factura.
     *
     * Contempla tres casos:
     *   A) Nombre + qty + precio en la misma línea (nombre corto)
     *   B) Nombre en las primeras líneas y después una línea con solo números (nombre largo)
     *   C) Nombre en las primeras líneas, y la última tiene un pedazo de nombre + números
     */
    private void parseProductSection(List<String> rawLines, OperationDto dto, String fullText) {
        // Regex: nombre (cualquier texto) qty  precioUnit  subtotal
        Pattern fullLine = Pattern.compile("^(.+?)\\s+(\\d+)\\s+([\\d,.]+)\\s+([\\d,.]+)\\s*$");
        // Regex: solo números — qty  precioUnit  subtotal (la línea que sigue a un nombre largo)
        Pattern numOnly  = Pattern.compile("^(\\d+)\\s+([\\d,.]+)\\s+([\\d,.]+)\\s*$");

        // Saco cualquier carácter del principio que no sea letra ni número:
        // viñetas (●•◆■), guiones (- y el en-dash que PDFBox mete como marcador de
        // ítem), asteriscos, espacios, etc. Uso clases Unicode para no depender del
        // símbolo exacto de la viñeta.
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            lines.add(l.replaceAll("^[^\\p{L}\\p{N}]+", "").trim());
        }

        StringBuilder accName = new StringBuilder();

        for (String line : lines) {
            // Caso A y C: la línea tiene nombre + números al final
            Matcher mFull = fullLine.matcher(line);
            if (mFull.find()) {
                String namePart = mFull.group(1).trim();
                // Junto el nombre que venía arrastrando de las líneas anteriores con
                // el pedazo de nombre que hay en esta línea
                String fullName = accName.length() > 0
                        ? accName + " " + namePart
                        : namePart;
                fullName = fullName.trim();
                dto.setProductName(fullName);
                dto.setProductSku(generateSku(fullName));
                dto.setQuantity(Integer.parseInt(mFull.group(2)));
                dto.setUnitPrice(parseMoney(mFull.group(3)));
                return;
            }

            // Caso B: la línea es SOLO números → el nombre completo ya lo tengo acumulado
            Matcher mNum = numOnly.matcher(line);
            if (mNum.find()) {
                String fullName = accName.length() > 0 ? accName.toString().trim() : "Producto";
                dto.setProductName(fullName);
                dto.setProductSku(generateSku(fullName));
                dto.setQuantity(Integer.parseInt(mNum.group(1)));
                dto.setUnitPrice(parseMoney(mNum.group(2)));
                return;
            }

            // La línea no tiene números que sirvan: es parte del nombre (nombre largo)
            if (accName.length() > 0) accName.append(" ");
            accName.append(line);
        }

        // Último recurso: uso el nombre que junté y busco el total en el documento
        String fallbackName = accName.length() > 0 ? accName.toString().trim() : "Producto";
        dto.setProductName(fallbackName);
        dto.setProductSku(generateSku(fallbackName));
        dto.setQuantity(1);
        dto.setUnitPrice(extractTotalFromDocument(fullText));
    }

    /**
     * Busca el precio total en todo el texto del documento, como último recurso.
     * Sirve cuando no se pudo sacar el precio de la sección de ítems.
     */
    private BigDecimal extractTotalFromDocument(String text) {
        String[] patterns = {
            "Importe Total[:\\s$]*([\\d.,]+)",
            "Total Factura[:\\s$]*([\\d.,]+)",
            "TOTAL[:\\s$]+([\\d.,]+)"
        };
        for (String p : patterns) {
            String val = extract(text, p, 1);
            if (val != null && !val.isBlank()) {
                return parseMoney(val);
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * Saca la provincia del comprador de la línea de dirección del PDF.
     *
     * El PDF tiene este formato:
     *   Dirección:
     *   [Calle] [número] [Provincia] AR
     *   Ciudad: [ciudad]
     *
     * La provincia es el último grupo de palabras en TitleCase justo antes de " AR"
     * al final de esa línea. La línea del vendedor también termina en " AR" pero
     * empieza con "CP:" — esa la descarto.
     *
     * Uso \p{Lu}/\p{Ll} para que tome las letras acentuadas del español
     * (Ó, É, Í, Á, Ú, Ñ…) sin tener que listarlas una por una.
     */
    private String extractProvince(String text) {
        // Patrón: última secuencia de palabras TitleCase del texto antes de " AR"
        // (pido al menos una minúscula después de la mayúscula inicial para descartar
        //  abreviaturas como "SN" = sin número, que van todas en MAYÚSCULAS)
        Pattern titleCase = Pattern.compile("[\\p{Lu}][\\p{Ll}]+(?:\\s+[\\p{Lu}][\\p{Ll}]+)*");

        for (String line : text.split("\\n")) {
            String t = line.trim();

            // La línea de dirección del comprador: termina en " AR", tiene dígitos
            // (el número de la calle), y NO es la cabecera del vendedor (empieza con "CP:")
            if (!t.endsWith(" AR") || !t.matches(".*\\d.*") || t.startsWith("CP:")) continue;

            // Saco el " AR" del final y busco el último grupo TitleCase
            String beforeAR = t.substring(0, t.length() - 3).trim();
            Matcher m = titleCase.matcher(beforeAR);
            String lastMatch = null;
            while (m.find()) {
                lastMatch = m.group();
            }
            if (lastMatch != null && lastMatch.length() > 2) {
                return mapProvince(lastMatch);
            }
        }
        return "Buenos Aires"; // por defecto
    }

    /**
     * Acomoda algunas variantes del nombre de provincia para que coincidan con las
     * claves que tengo guardadas en la tabla iibb_rates.
     * El PDF de Mercado Libre usa "Capital Federal" para CABA.
     */
    private String mapProvince(String raw) {
        if (raw.equalsIgnoreCase("Capital Federal")) return "CABA";
        return raw;
    }

    private String extract(String text, String regex, int group) {
        Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(group).trim();
        }
        return null;
    }

    private BigDecimal parseMoney(String value) {
        if (value == null) return BigDecimal.ZERO;

        // Cuento cuántos puntos y cuántas comas tiene, para saber cuál es el
        // separador de miles y cuál el decimal.
        int dotCount = 0;
        int commaCount = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.') dotCount++;
            if (c == ',') commaCount++;
        }

        String clean;
        if (dotCount > 1) {
            // Formato "13.800,00" → saco los puntos y la coma pasa a punto
            clean = value.replaceAll("\\.", "").replace(",", ".");
        } else if (commaCount >= 1 && dotCount == 1) {
            // Un solo punto y al menos una coma. Veo cuál está más a la derecha.
            int lastDot   = value.lastIndexOf('.');
            int lastComma = value.lastIndexOf(',');
            if (lastComma > lastDot) {
                // "13.800,00" → la coma es el decimal
                clean = value.replaceAll("\\.", "").replace(",", ".");
            } else {
                // "13,800.00" → el punto es el decimal (raro en AR pero por las dudas)
                clean = value.replace(",", "");
            }
        } else if (commaCount == 1 && dotCount == 0) {
            // "13800,00" → la coma es el decimal
            clean = value.replace(",", ".");
        } else {
            clean = value;
        }
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String generateSku(String name) {
        // Normalizo el nombre a una forma "limpia" sin acentos ni puntuación, para
        // que un mismo producto genere SIEMPRE el mismo SKU, sin importar cómo PDFBox
        // sacó los acentos en cada factura (a veces "Mandíbula", a veces "Mandibula").
        // NFD separa la tilde/ñ en letra + diacrítico (\p{M}), que después borro.
        String slug = java.text.Normalizer
                .normalize(name.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")        // saca tildes, diéresis, ~ de la ñ
                .replaceAll("[^a-z0-9 ]", "")    // saca comas, puntos y otros símbolos
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        return slug.substring(0, Math.min(60, slug.length()));
    }
}

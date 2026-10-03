package com.stock.external.opendart;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import com.stock.entity.Currency;
import com.stock.exception.ApiException;
import com.stock.external.OfficialApiErrors;
import com.stock.external.model.DividendData;
import com.stock.external.opendart.dto.OpenDartResponse;
import org.springframework.stereotype.Component;
@Component
public class OpenDartMapper {
    public static void checkStatus(String status) {
        if ("000".equals(status) || "013".equals(status)) return;
        if (status == null) throw ApiException.invalidResponse();
        if (Set.of("010", "011", "012", "901").contains(status))
            throw OfficialApiErrors.error("EXTERNAL_API_AUTH_FAILED", "OpenDART 인증키 또는 접근 권한을 확인해 주세요.");
        if ("020".equals(status)) throw ApiException.rateLimit();
        throw OfficialApiErrors.error("EXTERNAL_API_UNAVAILABLE", "OpenDART가 요청을 처리하지 못했습니다.");
    }
    private org.w3c.dom.Document xml(byte[] bytes) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void fatalError(org.xml.sax.SAXParseException ex) throws org.xml.sax.SAXException { throw ex; }
        });
        return builder.parse(new ByteArrayInputStream(bytes));
    }
    public Map<String,String> corporations(byte[] archive) {
        try {
            if (archive == null || archive.length < 4) throw ApiException.invalidResponse();
            if (archive[0] != 'P' || archive[1] != 'K') {
                var statuses = xml(archive).getElementsByTagName("status");
                checkStatus(statuses.getLength() == 0 ? null : statuses.item(0).getTextContent());
                throw ApiException.invalidResponse();
            }
            try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (!"CORPCODE.xml".equals(entry.getName())) continue;
                    byte[] bytes = zip.readNBytes(64 * 1024 * 1024 + 1);
                    if (bytes.length > 64 * 1024 * 1024) throw ApiException.invalidResponse();
                    var rows = xml(bytes).getElementsByTagName("list");
                    Map<String,String> result = new HashMap<>();
                    for (int i = 0; i < rows.getLength(); i++) {
                        var row = (org.w3c.dom.Element) rows.item(i);
                        String ticker = field(row, "stock_code").strip();
                        if (ticker.isBlank()) continue;
                        String code = field(row, "corp_code").strip();
                        if (!ticker.matches("[0-9A-Z]{6}") || !code.matches("[0-9]{8}")) throw ApiException.invalidResponse();
                        String previous = result.putIfAbsent(ticker, code);
                        if (previous != null && !previous.equals(code)) throw ApiException.invalidResponse();
                    }
                    if (result.isEmpty()) throw ApiException.invalidResponse();
                    return Map.copyOf(result);
                }
            }
            throw ApiException.invalidResponse();
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) { throw ApiException.invalidResponse(); }
    }
    private String field(org.w3c.dom.Element row, String name) {
        var fields = row.getElementsByTagName(name);
        return fields.getLength() == 1 ? fields.item(0).getTextContent() : "";
    }
    public List<DividendData> dividends(OpenDartResponse.Data response, String corporation, int year, String reportCode) {
        if (response == null) throw ApiException.invalidResponse();
        checkStatus(response.status());
        if ("013".equals(response.status())) return List.of();
        if (response.list() == null) throw ApiException.invalidResponse();
        List<DividendData> result = new ArrayList<>();
        for (var row : response.list()) {
            if (row == null || !corporation.equals(row.corporation())) throw ApiException.invalidResponse();
            // Only the actual current-period per-common-share cash amount, never total cash/yield/prior periods.
            if (!"주당 현금배당금(원)".equals(row.se()) || !"보통주".equals(row.shareClass())) continue;
            String raw = row.thstrm();
            if (raw == null) throw ApiException.invalidResponse();
            if (raw.strip().equals("-") || raw.isBlank()) continue; // Unavailable is not zero.
            if (!raw.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?") || row.filingNumber() == null
                    || !row.filingNumber().matches("[0-9]{14}")) throw ApiException.invalidResponse();
            BigDecimal amount = new BigDecimal(raw.replace(",", ""));
            if (amount.scale() > 8 || amount.precision() - amount.scale() > 16) throw ApiException.invalidResponse();
            result.add(new DividendData(amount, null, null, null, Currency.KRW, year, reportCode, row.filingNumber(), row.shareClass()));
        }
        if (result.size() > 1) throw ApiException.invalidResponse(); // Conflicting share classes/duplicate report rows require explicit handling.
        return List.copyOf(result);
    }
}

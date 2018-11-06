package com.smockin.mockserver.proxy;

import com.smockin.admin.dto.HttpClientCallDTO;
import com.smockin.admin.dto.response.HttpClientResponseDTO;
import com.smockin.admin.persistence.enums.RestMethodEnum;
import com.smockin.mockserver.dto.ProxyActiveMock;
import com.smockin.utils.GeneralUtils;
import com.smockin.utils.HttpClientUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.apache.commons.lang3.StringUtils;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ProxyServerUtils {

    private final Logger logger = LoggerFactory.getLogger(ProxyServerUtils.class);

    private static final String MOCK_SERVER_HOST = "http://localhost:";
    static final String PATH_VAR_REGEX = "[a-zA-Z0-9_+-._~:!$&'()*+,=@]+";

    HttpResponse buildBadResponse() {
        return buildResponse(new HttpClientResponseDTO(HttpStatus.BAD_REQUEST.value(), "text/html; charset=UTF-8", new HashMap<>() ,""));
    }

    HttpResponse buildResponse(final HttpClientResponseDTO dto) {
        logger.debug("buildResponse called");

        final byte[] bytes = dto.getBody().getBytes(Charset.forName("UTF-8"));
        final ByteBuf content = Unpooled.copiedBuffer(bytes);
        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(dto.getStatus()), content);
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bytes.length);
        response.headers().set("Content-Type", dto.getContentType());
        response.headers().set("Date", ProxyUtils.formatDate(new Date()));
        response.headers().set(HttpHeaders.Names.CONNECTION, "close");

        dto.getHeaders()
                .entrySet()
                .stream()
                .forEach(h -> response.headers().set(h.getKey(), h.getValue()));

        return response;
    }

    HttpClientCallDTO buildRequestDTO(final LittleProxyContext context, final RestMethodEnum method, final String destUrl) {
        logger.debug("buildRequestDTO called");

        debugMockDestRequestDTO(context, method, destUrl);

        final HttpClientCallDTO dto = new HttpClientCallDTO(destUrl, method);

        if (RestMethodEnum.POST.equals(method)
                || RestMethodEnum.PUT.equals(method)) {
            dto.setBody(context.getRequestBody());
        }

        context.getRequestHeaders().stream().forEach(h ->
            dto.getHeaders().put(h.getKey(), h.getValue())
        );

        // Used by mock server to hide live logging.
        dto.getHeaders().put(GeneralUtils.PROXY_MOCK_INTERCEPT_HEADER, "true");

        return dto;
    }

    boolean doesPathMatch(final String inboundPath, final String path) {

        String newMockPathRegex = path;
        String mockPath = path;
        int mockPathVarIdx;

        while ((mockPathVarIdx = mockPath.indexOf(":")) > -1) {

            final int pathVarEnd = mockPath.indexOf("/", mockPathVarIdx);
            final int pathVarEndIdx = (pathVarEnd > -1) ? pathVarEnd : mockPath.length();
            final String part = mockPath.substring(mockPathVarIdx, pathVarEndIdx);

            newMockPathRegex = StringUtils.replaceOnce(newMockPathRegex, part, PATH_VAR_REGEX);

            mockPath = mockPath.substring(pathVarEndIdx, mockPath.length());
        }

        return inboundPath.matches("^" + newMockPathRegex + "$");
    }

    String buildMockUrl(final URL inboundUrl, final int mockServerPort, final String userCtx) {
        logger.debug("buildMockUrl called");

        final StringBuilder mockUrl = new StringBuilder();
        mockUrl.append(MOCK_SERVER_HOST);
        mockUrl.append(mockServerPort);
        if (StringUtils.isNotBlank(userCtx)) {
            mockUrl.append("/");
            mockUrl.append(userCtx);
        }
        mockUrl.append(inboundUrl.getPath());

        if (inboundUrl.getQuery() != null) {
            mockUrl.append("?");
            mockUrl.append(inboundUrl.getQuery());
        }

        return mockUrl.toString();
    }

    List<ProxyActiveMock> findMockPathMatches(final String inboundPath, final List<ProxyActiveMock> activeMocks) {
        logger.debug("checkForMockPathMatch called");

        // Need to think about order of paths which start with same value (i.e /house, /house/people/bob)
        return activeMocks
                .stream()
                .filter(e -> doesPathMatch(inboundPath, e.getPath()))
                .collect(Collectors.toList());
    }

    Optional<ProxyActiveMock> findMockMethodMatch(final String method, final List<ProxyActiveMock> pathMatches) {
        logger.debug("findMockMethodMatch called");

        if (pathMatches.isEmpty()) {
            return Optional.empty();
        }

        return pathMatches
                .stream()
                .filter(m -> m.getMethod().name().equalsIgnoreCase(method))
                .findFirst();
    }

    Optional<ProxyActiveMock> findMockMatch(final HttpRequest originalRequest, final List<ProxyActiveMock> activeMocks) throws MalformedURLException {

        final List<ProxyActiveMock> pathMatches =
                findMockPathMatches(new URL(fixProtocolWithDummyPrefix(originalRequest.getUri())).getPath(), activeMocks);

        return findMockMethodMatch(originalRequest.getMethod().name(), pathMatches);
    }

    HttpClientResponseDTO callMock(final HttpClientCallDTO dto) throws IOException {
        logger.debug("callMock called");

        sanitizeRequestHeaders(dto);

        switch (dto.getMethod()) {
            case GET:
                return HttpClientUtils.get(dto);
            case POST:
                return HttpClientUtils.post(dto);
            case PUT:
                return HttpClientUtils.put(dto);
            case DELETE:
                return HttpClientUtils.delete(dto);
            case PATCH:
                return HttpClientUtils.patch(dto);
            default:
                return null;
        }

    }

    void sanitizeRequestHeaders(final HttpClientCallDTO dto) {

        // BUG Fix. HTTPClient has a problem when the 'Content-Length' header is set so filtering it out here...
        dto.setHeaders(dto.getHeaders()
                .entrySet()
                .stream()
                .filter(h -> !"Content-Length".equalsIgnoreCase(h.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    boolean excludeInboundMethod(final String method) {

        return ("CONNECT".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)
                || "TRACE".equalsIgnoreCase(method));
    }

    String fixProtocolWithDummyPrefix(final String uri) {
        logger.debug("fixProtocolWithDummyPrefix called");

        if (!uri.startsWith("http")) {
            return "https://dummyhost" + uri;
        }

        return uri;
    }

    void debugInboundRequest(HttpRequest req) {

        if (logger.isDebugEnabled()) {
            logger.debug("Inbound URI: " + req.getUri());
            logger.debug("Inbound method: " + req.getMethod());
            logger.debug("Inbound HTTP version: " + req.getProtocolVersion());
        }
    }

    private void debugMockDestRequestDTO(final LittleProxyContext context, final RestMethodEnum method, final String destUrl) {

        if (logger.isDebugEnabled()) {
            logger.debug("dest url " + destUrl);
            logger.debug("dest method " + method);
            logger.debug("dest body " + context.getRequestBody());

            logger.debug("dest headers");
            context.getRequestHeaders().stream().forEach(h ->
                    logger.debug(h.getKey() + ": " + h.getValue())
            );
        }
    }

}

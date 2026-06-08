    package net.boyuan.stockmentor.common.exception;
    
    import org.junit.jupiter.api.Test;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.server.ResponseStatusException;
    
    import java.util.Map;
    
    import static org.junit.jupiter.api.Assertions.*;
    
    class GlobalExceptionHandlerTests {
        private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    
        @Test
        void responseStatusExceptionPreservesUnauthorizedStatusAndMessage() {
            ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token")
            );
    
            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getBody().get("status"));
            assertEquals("Invalid admin token", response.getBody().get("message"));
        }
    
        @Test
        void responseStatusExceptionPreservesInternalServerErrorStatusAndMessage() {
            ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(
                    new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Admin token is not configured")
            );
    
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().get("status"));
            assertEquals("Admin token is not configured", response.getBody().get("message"));
        }
    
        @Test
        void responseStatusExceptionPreservesBadRequestStatusAndMessage() {
            ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported backfill type: BAD")
            );
    
            assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().get("status"));
            assertEquals("Unsupported backfill type: BAD", response.getBody().get("message"));
        }
    }

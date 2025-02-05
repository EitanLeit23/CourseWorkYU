import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.stage1.DocumentStore;
import edu.yu.cs.com1320.project.stage1.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage1.impl.DocumentStoreImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public class TestClass{
    @Test
    public void documentBlankUri(){
        URI uriBlank = null;
        String blank = "";
        String notBlankStr = "Hello!";
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new DocumentImpl(uriBlank, notBlankStr);
        });
    }
    @Test
    public void documentBlankString() throws java.net.URISyntaxException{
        URI uri = new URI("https://www.example.com/path/to/resource?param=value");
        String blank = "";
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new DocumentImpl(uri, blank);
        });

    }
    @Test
    public void nullUri() throws java.net.URISyntaxException{
        URI uri = null;
        String nul = "stringledingle";
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new DocumentImpl(uri, nul);
        });
    }
    @Test
    public void nullString() throws java.net.URISyntaxException{
        URI uri = new URI("https://www.example.com/path/to/resource?param=value");
        String nul = null;
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new DocumentImpl(uri, nul);
        });
    }
    @Test
    public void emptyByteArray() throws java.net.URISyntaxException{
        URI uri = new URI("https://www.example.com/path/to/resource?param=value");
        byte[] empty = new byte[0];
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new DocumentImpl(uri, empty);
        });
    }
    @Test
    public void blankByteArray() throws java.net.URISyntaxException{
        URI uri = new URI("https://www.example.com/path/to/resource?param=value");
        byte[] empty = {0, 0, 0, 0, 0, 0, 0, 0, 0};
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new DocumentImpl(uri, empty);
        });
    }
    @Test
    public void nullByteArray() throws java.net.URISyntaxException{
        URI uri = new URI("https://www.example.com/path/to/resource?param=value");
        byte[] empty = null;
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new DocumentImpl(uri, empty);
        });
    }
    @Test
    public void workingDocumentStr() throws java.net.URISyntaxException{
        URI uri = new URI("https://www.example.com/path/to/resource?param=value");
        String str = "squiggle";
        DocumentImpl doc = new DocumentImpl(uri, str);
        DocumentImpl doc1 = new DocumentImpl(uri, str);
        assertEquals(str, doc.getDocumentTxt());
        assertArrayEquals(null, doc.getDocumentBinaryData());
        assertTrue(doc.equals(doc1));
        System.out.println(doc.hashCode());
    }
    @Test
    public void workingDocumentBytes() throws java.net.URISyntaxException{
        URI uri = new URI("https://www.example.com/path/to/resource?param=value");
        String str = "squiggle";
        byte[] bytes = str.getBytes();
        DocumentImpl doc1 = new DocumentImpl(uri, str);
        DocumentImpl doc = new DocumentImpl(uri, bytes);
        assertEquals(null, doc.getDocumentTxt());
        assertArrayEquals(str.getBytes(), doc.getDocumentBinaryData());
        assertFalse(doc.equals(doc1));
        System.out.println(doc.hashCode());
    }
    @Test
    public void hashTableTest(){
        HashTableImpl<String, Integer> table = new HashTableImpl();
        table.put("one", 1);
        table.put("two", 2);
        table.put("three", 3);
        assertTrue(table.containsKey("one"));
        assertEquals(table.get("two"), 2);
        table.put("two", null);
        assertFalse(table.containsKey("two"));
        table.put("FBFCD", 1); //according to chat gpt these ahve the same hashcode
        table.put("GCEDA", 2);
        table.put("EBFDC", 3);
        assertEquals(table.get("FBFCD"), 1);
        assertEquals(table.get("GCEDA"), 2);
        assertEquals(table.get("EBFDC"), 3);
        table.put("EBFDC", null);
        assertFalse(table.containsKey("EBFDC"));
        Integer integer = table.put("GCEDA", 3);
        assertEquals(table.get("GCEDA"), 3);
        assertEquals(integer, 2); // checks if changing a value returns the old one
        assertNull(table.get("StringlyDingle"));
    }
    @Test
    public void documentStoreImplTest() throws IOException, URISyntaxException {
        URI validUri = new URI("src/bru.txt");
        File poem = new File("src/bru.txt");
        InputStream validInput = new FileInputStream(poem);
        DocumentStoreImpl documentStore = new DocumentStoreImpl();
        documentStore.put(validInput, validUri, DocumentStore.DocumentFormat.TXT);
        assertNotNull(documentStore.get(validUri));
        DocumentImpl doc = (DocumentImpl)documentStore.get(validUri);
        System.out.println(doc.getDocumentTxt());
        documentStore.delete(validUri);
        assertNull(documentStore.get((validUri)));
        validInput = new FileInputStream(poem);
        assertEquals(0, documentStore.put(validInput, validUri, DocumentStore.DocumentFormat.TXT));
        System.out.println(documentStore.put(null, validUri, DocumentStore.DocumentFormat.TXT));
        validInput = new FileInputStream(poem);
        InputStream finalValidInput = validInput;
        assertThrows(IllegalArgumentException.class, () -> {
            documentStore.put(finalValidInput, validUri, null);
        });
        validInput = new FileInputStream(poem);
        InputStream finalValidInput1 = validInput;
        assertThrows(IllegalArgumentException.class, () -> {
            documentStore.put(finalValidInput1, null, DocumentStore.DocumentFormat.TXT);
        });
    }

    @Test
    public void hashCodeTest() {
        assertEquals(0, Arrays.hashCode((byte[])null));
    }
}

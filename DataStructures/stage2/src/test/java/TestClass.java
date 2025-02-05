import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.stage2.DocumentStore;
import edu.yu.cs.com1320.project.stage2.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage2.impl.DocumentStoreImpl;
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
    public void stackImplTest(){
        StackImpl<Integer> stack = new StackImpl<>();
        stack.push(1);
        stack.push(2);
        assertEquals(2, stack.size());
        assertEquals(2, stack.pop());
        assertEquals(1, stack.pop());
        assertNull(stack.pop());
        stack.push(null);
        assertNull(stack.peek());
        assertNull(stack.pop());
        stack.push(1);
        stack.push(2);
        assertEquals(2, stack.peek());
        assertEquals(2, stack.pop());
        assertEquals(1, stack.peek());
        assertEquals(1, stack.pop());
        assertNull(stack.peek());
        assertNull(stack.pop());
        for(Integer i = 0; i < 50; i++){
            stack.push(i);
        }
        for(int i = 49; i >= 0; i--){
            assertEquals(i, stack.pop());
        }
    }
    @Test
    public void hashTableImplementResizeTest(){
        HashTableImpl<String, Integer> hashTable = new HashTableImpl<>();
        for(int i = 0; i < 10000; i++){
            String str = "A" + i;
            hashTable.put(str,i);
        }
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

    @Test
    public void undoTest() throws IOException, URISyntaxException{
        URI validUri = new URI("src/bru.txt");
        File poem = new File("src/bru.txt");
        InputStream validInput = new FileInputStream(poem);
        DocumentStoreImpl documentStore = new DocumentStoreImpl();
        documentStore.put(validInput, validUri, DocumentStore.DocumentFormat.TXT);
        DocumentImpl doc = (DocumentImpl)documentStore.get(validUri);
        System.out.println(doc.getDocumentTxt());
        documentStore.undo();
        assertNull(documentStore.get(validUri));
        assertThrows(IllegalStateException.class, () -> {
            documentStore.undo();
        });
        validInput = new FileInputStream(poem);
        documentStore.put(validInput, validUri, DocumentStore.DocumentFormat.TXT);
        documentStore.delete(validUri);
        documentStore.undo();
        doc = (DocumentImpl)documentStore.get(validUri);
        String str = doc.getDocumentTxt();
        assertEquals("what am I doing at 11:30 at night\r\n" +
                "I feel that i'm close, but losing my might\r\n" +
                "the comp sci grind is pretty rough\r\n" +
                "i hope all the testing i did is enough\r\n" +
                "*snaps*", str);
        validInput = new FileInputStream(poem);
        documentStore.put(validInput, validUri, DocumentStore.DocumentFormat.TXT);
        documentStore.put(null, validUri,DocumentStore.DocumentFormat.TXT);
        documentStore.undo();
        doc = (DocumentImpl)documentStore.get(validUri);
        str = doc.getDocumentTxt();
        assertEquals("what am I doing at 11:30 at night\r\n" +
                "I feel that i'm close, but losing my might\r\n" +
                "the comp sci grind is pretty rough\r\n" +
                "i hope all the testing i did is enough\r\n" +
                "*snaps*", str);
    }
    @Test
    public void parametricUndo() throws IOException, URISyntaxException{
        URI validUri1 = new URI("src/bru.txt");
        File poem1 = new File("src/bru.txt");
        URI validUri2 = new URI("src/bru2.0.txt");
        File poem2 = new File("src/bru2.0.txt");
        InputStream validInput1 = new FileInputStream(poem1);
        InputStream validInput2 = new FileInputStream(poem2);
        DocumentStoreImpl documentStore = new DocumentStoreImpl();
        documentStore.put(validInput1, validUri1, DocumentStore.DocumentFormat.TXT);
        documentStore.put(validInput2, validUri2, DocumentStore.DocumentFormat.TXT);
        assertEquals(validUri1, documentStore.get(validUri1).getKey());
        documentStore.undo(validUri1);
        assertNull(documentStore.get(validUri1));
        validInput1 = new FileInputStream(poem1);
        documentStore.put(validInput1, validUri1, DocumentStore.DocumentFormat.TXT);
        documentStore.undo(validUri2);
        documentStore.undo(validUri1);
        assertNull(documentStore.get(validUri1));
        assertNull(documentStore.get(validUri2));
        validInput1 = new FileInputStream(poem1);
        validInput2 = new FileInputStream(poem2);
        documentStore.put(validInput1, validUri1, DocumentStore.DocumentFormat.TXT);
        documentStore.put(validInput2, validUri2, DocumentStore.DocumentFormat.TXT);
        assertFalse(documentStore.get(validUri1).getDocumentTxt().equals(documentStore.get(validUri2).getDocumentTxt()));
        validInput1 = new FileInputStream(poem1);
        documentStore.put(validInput1, validUri2, DocumentStore.DocumentFormat.TXT);
        assertTrue(documentStore.get(validUri1).getDocumentTxt().equals(documentStore.get(validUri2).getDocumentTxt()));
        documentStore.undo(validUri2);
        assertFalse(documentStore.get(validUri1).getDocumentTxt().equals(documentStore.get(validUri2).getDocumentTxt()));
        DocumentStoreImpl documentStore1 = new DocumentStoreImpl();
        assertThrows(IllegalStateException.class, () -> {
            documentStore1.undo(validUri2);
        });

    }
}

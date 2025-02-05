import edu.yu.cs.com1320.project.impl.BTreeImpl;
import edu.yu.cs.com1320.project.impl.WrongBTree;
import edu.yu.cs.com1320.project.stage5.Document;
import edu.yu.cs.com1320.project.stage5.DocumentStore;
import edu.yu.cs.com1320.project.stage5.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage5.impl.DocumentPersistenceManager;
import edu.yu.cs.com1320.project.stage5.impl.DocumentStoreImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
public class testClass {

    @Test
    public void bTreeTest() {
        BTreeImpl<Integer, String> st = new BTreeImpl<>();
        st.put(1, "one");
        st.put(2, "two");
        st.put(3, "three");
        st.put(4, "four");
        st.put(5, "five");
        st.put(6, "six");
        st.put(7, "seven");
        st.put(8, "eight");
        st.put(9, "nine");
        st.put(10, "ten");
        st.put(11, "eleven");
        st.put(12, "twelve");
        st.put(13, "thirteen");
        st.put(14, "fourteen");
        st.put(15, "fifteen");
        st.put(16, "sixteen");
        st.put(17, "seventeen");
        st.put(18, "eighteen");
        st.put(19, "nineteen");
        st.put(20, "twenty");
        st.put(21, "twenty one");
        st.put(22, "twenty two");
        st.put(23, "twenty three");
        st.put(24, "twenty four");
        st.put(25, "twenty five");
        st.put(26, "twenty six");
        assertEquals("one", st.get(1));
        assertEquals("two", st.get(2));
        assertEquals("three", st.get(3));
        assertEquals("four", st.get(4));
        assertEquals("five", st.get(5));
        assertEquals("twenty six", st.get(26));
        String testReplace = st.put(3, "Three");
        assertEquals("three", testReplace);
        assertEquals("Three", st.get(3));
    }

    @Test
    public void DocPerManTest() throws URISyntaxException, IOException {
        DocumentPersistenceManager docPerMan = new DocumentPersistenceManager(null);
        URI uri1 = new URI("Stage5/src/file");
        DocumentImpl doc1 = new DocumentImpl(uri1, "hello", null);
        docPerMan.serialize(uri1, doc1);
        assertEquals("hello", docPerMan.deserialize(uri1).getDocumentTxt());
        assertTrue(docPerMan.delete(uri1));
        DocumentImpl doc2 = new DocumentImpl(uri1, "a", null);
        docPerMan.serialize(uri1, doc2);
        docPerMan.deserialize(uri1);
        docPerMan.serialize(uri1, doc2);
        docPerMan.delete(uri1);
    }

    @Test
    public void BTreeImplTest() throws Exception {
        BTreeImpl<URI, Document> bTree = new BTreeImpl<>();
        bTree.setPersistenceManager(new DocumentPersistenceManager(null));
        URI uri1 = new URI("Stage5/src/file");
        DocumentImpl doc1 = new DocumentImpl(uri1, "hello", null);
        bTree.put(uri1, doc1);
        assertEquals(doc1, bTree.get(uri1));
        bTree.moveToDisk(uri1);
        assertEquals(doc1, bTree.get(uri1));
        bTree.moveToDisk(uri1);
        DocumentImpl doc2 = new DocumentImpl(uri1, "yo", null);
        bTree.put(uri1, doc2);
        assertEquals(doc2, bTree.get(uri1));
        bTree.moveToDisk(uri1);
        assertEquals(doc2, bTree.get(uri1));
        URI uri2 = new URI("Stage5/src/file2");
        String str = "hello";
        byte[] bytes = str.getBytes();
        DocumentImpl doc3 = new DocumentImpl(uri2, bytes);
        long long1 = doc3.getLastUseTime();
        bTree.put(uri2, doc3);
        assertEquals(doc3, bTree.get(uri2));
        bTree.moveToDisk(uri2);
        assertEquals(doc3, bTree.get(uri2));
        long long2 = bTree.get(uri2).getLastUseTime();
        assertTrue(long2 > long1);
    }

    @Test
    public void documentStoreTest() throws URISyntaxException, IOException {
        String txt1 = "today is the THe tHE day that 89 ?*";
        String txt2 = "IS is THE day ho4 is 89 89 % ^ # f58";
        String txt3 = "this is The is the txt3 ho4";
        URI uri1 = new URI("Stage5/src/files/uri1");
        URI uri2 = new URI("Stage5/src/files/uri2");
        URI uri3 = new URI("Stage5/src/files/uri3");
        DocumentImpl doc1 = new DocumentImpl(uri1, txt1, null);
        DocumentImpl doc2 = new DocumentImpl(uri2, txt2, null);
        DocumentImpl doc3 = new DocumentImpl(uri3, txt3, null);
        InputStream input1 = new ByteArrayInputStream(txt1.getBytes(StandardCharsets.UTF_8));
        InputStream input2 = new ByteArrayInputStream(txt2.getBytes(StandardCharsets.UTF_8));
        InputStream input3 = new ByteArrayInputStream(txt3.getBytes(StandardCharsets.UTF_8));
        DocumentStoreImpl documentStore = new DocumentStoreImpl();
        documentStore.put(input1, uri1, DocumentStore.DocumentFormat.TXT);
        documentStore.put(input2, uri2, DocumentStore.DocumentFormat.TXT);
        documentStore.put(input3, uri3, DocumentStore.DocumentFormat.TXT);
        assertEquals(doc1, documentStore.get(uri1));
        assertEquals(doc2, documentStore.get(uri2));
        assertEquals(doc3, documentStore.get(uri3));
        documentStore.setMaxDocumentCount(2);
        assertEquals(doc1, documentStore.get(uri1));
        assertEquals(doc2, documentStore.get(uri2));
        assertEquals(doc3, documentStore.get(uri3));
        documentStore.delete(uri1);
        documentStore.undo();
        assertEquals(doc1, documentStore.get(uri1));

        System.out.println(documentStore.deleteAllWithPrefix("th"));
        assertNotEquals(doc1, documentStore.get(uri1));
        //assertNotEquals(doc2, documentStore.get(uri2));
        assertNotEquals(doc3, documentStore.get(uri3));
        documentStore.undo();
        assertEquals(doc1, documentStore.get(uri1));
        assertEquals(doc2, documentStore.get(uri2));
        assertEquals(doc3, documentStore.get(uri3));
        documentStore.delete(uri3);
        documentStore.delete(uri1);
        documentStore.delete(uri2);
        documentStore.setMaxDocumentCount(0);
        input1 = new ByteArrayInputStream(txt1.getBytes(StandardCharsets.UTF_8));
        input2 = new ByteArrayInputStream(txt2.getBytes(StandardCharsets.UTF_8));
        input3 = new ByteArrayInputStream(txt3.getBytes(StandardCharsets.UTF_8));
        documentStore.put(input1, uri1, DocumentStore.DocumentFormat.TXT);
        documentStore.put(input2, uri2, DocumentStore.DocumentFormat.TXT);
        documentStore.put(input3, uri3, DocumentStore.DocumentFormat.TXT);
        documentStore.deleteAllWithPrefix("is");
        assertNull(documentStore.get(uri1));
        assertNull(documentStore.get(uri2));
        assertNull(documentStore.get(uri3));
        //System.out.println(documentStore.searchByPrefix("is"));
        documentStore.undo();
        assertEquals(doc1, documentStore.get(uri1));
        assertEquals(doc2, documentStore.get(uri2));
        assertEquals(doc3, documentStore.get(uri3));
        documentStore.deleteAll("is");
        assertNull(documentStore.get(uri1));
        assertNull(documentStore.get(uri2));
        assertNull(documentStore.get(uri3));
        documentStore.undo();
        assertEquals(doc1, documentStore.get(uri1));
        assertEquals(doc2, documentStore.get(uri2));
        assertEquals(doc3, documentStore.get(uri3));
        documentStore.deleteAll("is");
    }
}

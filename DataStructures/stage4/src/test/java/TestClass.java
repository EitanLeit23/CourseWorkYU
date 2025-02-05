import edu.yu.cs.com1320.project.stage4.Document;
import edu.yu.cs.com1320.project.stage4.DocumentStore;
import edu.yu.cs.com1320.project.stage4.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage4.impl.DocumentStoreImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import static java.lang.System.nanoTime;

import javax.print.Doc;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class TestClass {
    private String txt1;
    private String txt2;
    private String txt3;
    private URI uri1;
    private URI uri2;
    private URI uri3;
    private Document doc1;
    private Document doc2;
    private Document doc3;
    private DocumentStoreImpl store;
    private InputStream input1;
    private InputStream input2;
    private InputStream input3;

    @BeforeEach
    public void init() throws Exception{
            this.txt1 = "today is the THe tHE day that 89 ?*";
            this.txt2 = "IS is THE day ho4 is 89 89 % ^ # f58";
            this.txt3 = "this is The is the txt3 ho4";
            this.uri1 = new URI("uri1");
            this.uri2 = new URI("uri2");
            this.uri3 = new URI("uri3");
            this.doc1 = new DocumentImpl(uri1, txt1);
            this.doc2 = new DocumentImpl(uri2, txt2);
            this.doc3 = new DocumentImpl(uri3, txt3);
            this.store = new DocumentStoreImpl();
            this.input1 = new ByteArrayInputStream(this.txt1.getBytes(StandardCharsets.UTF_8));
            this.input2 = new ByteArrayInputStream(this.txt2.getBytes(StandardCharsets.UTF_8));
            this.input3 = new ByteArrayInputStream(this.txt3.getBytes(StandardCharsets.UTF_8));

        }
    @Test
    public void minHeapImplTest(){
        MinHeapImpl<Integer> heap = new MinHeapImpl<>();
        heap.insert(2);
        heap.insert(1);
        assertEquals(1, heap.remove());
        heap.insert(3);
        heap.insert(4);
        heap.reHeapify(4);
        assertEquals(2, heap.remove());
    }
    @Test
    public void documentMinHeapTest(){
        MinHeapImpl<Document> heap = new MinHeapImpl<>();
        heap.insert(doc1);
        heap.insert(doc2);
        heap.insert(doc3);
        assertEquals(doc1, heap.remove());
        doc1.setLastUseTime(nanoTime());
        heap.insert(doc1);
        doc2.setLastUseTime(nanoTime());
        heap.reHeapify(doc2);
        assertEquals(doc3, heap.remove());
        assertEquals(doc1, heap.remove());
        doc1.setLastUseTime(nanoTime());
        doc3.setLastUseTime(nanoTime());
        heap.insert(doc1);
        heap.insert(doc3);
        assertEquals(doc2, heap.remove());
        heap.insert(doc2);
        doc2.setLastUseTime(nanoTime());
        heap.reHeapify(doc2);
        assertEquals(doc1, heap.remove());
        doc3.setLastUseTime(nanoTime());
        heap.reHeapify(doc3);
        assertEquals(doc2, heap.remove());
        heap.insert(doc2);
        heap.reHeapify(doc2);
        assertEquals(doc2, heap.remove());
    }

    @Test
    public void storeTest() throws IOException {
        store.put(input1, uri1, DocumentStore.DocumentFormat.TXT);
        store.put(input2, uri2, DocumentStore.DocumentFormat.TXT);
        store.put(input3, uri3, DocumentStore.DocumentFormat.TXT);
        store.delete(uri1);
        assertNull(this.store.get(uri1));
        assertEquals(this.doc2, this.store.get(uri2));
        assertEquals(this.doc3, this.store.get(uri3));
        this.store.put(new ByteArrayInputStream(this.txt1.getBytes(StandardCharsets.UTF_8)), uri1, DocumentStore.DocumentFormat.TXT);
        this.store.setMaxDocumentCount(2);
        assertNull(this.store.get(uri2));
        assertEquals(doc1, this.store.get(uri1));
        assertEquals(doc3, this.store.get(uri3));
        this.store.put(new ByteArrayInputStream(this.txt2.getBytes(StandardCharsets.UTF_8)), uri2, DocumentStore.DocumentFormat.TXT);
        assertNull(this.store.get(uri1));
        assertEquals(doc2, this.store.get(uri2));
        assertEquals(doc3, this.store.get(uri3));
        this.store.undo();
        assertNull(this.store.get(uri1));
    }
    @Test
    public void undoTest() throws IOException{
        this.store.put(input1, uri1, DocumentStore.DocumentFormat.TXT);
        this.store.put(input2, uri2, DocumentStore.DocumentFormat.TXT);
        this.store.put(input3, uri3, DocumentStore.DocumentFormat.TXT);
        this.store.deleteAll("is");
        assertNull(this.store.get(uri1));
        assertNull(this.store.get(uri2));
        assertNull(this.store.get(uri3));
        this.store.undo();
        assertEquals(this.store.get(uri1), doc1);
        assertEquals(this.store.get(uri2), doc2);
        assertEquals(this.store.get(uri3), doc3);
        this.store.deleteAll("is");
        this.store.setMaxDocumentCount(2);
        this.store.undo();
        assertNull(this.store.get(uri2));
        this.store.setMaxDocumentCount(3);
        this.store.put(new ByteArrayInputStream(this.txt2.getBytes()), uri2, DocumentStore.DocumentFormat.TXT);
        assertEquals(this.store.get(uri1), doc1);
        assertEquals(this.store.get(uri2), doc2);
        assertEquals(this.store.get(uri3), doc3);
        this.store.deleteAllWithPrefix("is");
        assertNull(this.store.get(uri1));
        assertNull(this.store.get(uri2));
        assertNull(this.store.get(uri3));
        this.store.undo(uri1);
        assertEquals(this.store.get(uri1), doc1);
        assertEquals(this.store.get(uri2), doc2);
        assertEquals(this.store.get(uri3), doc3);
        this.store.setMaxDocumentCount(2);
        assertNull(this.store.get(uri1));
        assertEquals(this.store.get(uri2), doc2);
        assertEquals(this.store.get(uri3), doc3);
        assertThrows(IllegalStateException.class, () -> {
            this.store.undo(null);
        });
    }

}

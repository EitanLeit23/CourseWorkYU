import edu.yu.cs.com1320.project.stage3.Document;
import edu.yu.cs.com1320.project.stage3.DocumentStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import edu.yu.cs.com1320.project.impl.*;
import edu.yu.cs.com1320.project.*;
import edu.yu.cs.com1320.project.stage3.impl.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class TestClass {
    public class intComparator implements Comparator<Integer>{
        @Override
        public int compare(Integer o1, Integer o2){
            return -1 * Integer.compare(o1, o2);
        }
    }
    @Test
    public void trieTest(){
        TrieImpl<Integer> trie = new TrieImpl<Integer>();
        trie.put("hello", 2);
        trie.put("hello", 3);
        trie.put("he", 1);
        List<Integer> values= trie.getAllSorted("hello", new intComparator());
        List<Integer> vals = trie.getAllWithPrefixSorted("h", new intComparator());
        System.out.println(values);
        System.out.println(vals);
        trie.delete("hello", 2);
        values= trie.getAllSorted("hello", new intComparator());
        vals = trie.getAllWithPrefixSorted("h", new intComparator());
        System.out.println(values);
        System.out.println(vals);
        trie.put("hello", 2);
        System.out.println(trie.getAllWithPrefixSorted("he", new intComparator()));
        System.out.println("test" + trie.deleteAll("hello"));
        System.out.println(trie.getAllWithPrefixSorted("he", new intComparator()));
        trie.put("hello", 2);
        System.out.println("get prefix" + trie.getAllWithPrefixSorted("h", new intComparator()));
        System.out.println("delete text" + trie.deleteAllWithPrefix("h"));
        System.out.println(trie.getAllWithPrefixSorted("he", new intComparator()));
        System.out.println(trie.getAllWithPrefixSorted("h", new intComparator()));
        System.out.println(trie.getAllWithPrefixSorted("h", new intComparator()));
        trie.put("he", 1);
        trie.put("h", 2);
        System.out.println(trie.getAllWithPrefixSorted("he", new intComparator()));
        System.out.println(trie.getAllWithPrefixSorted("h", new intComparator()));
        System.out.println(trie.getAllWithPrefixSorted("hell", new intComparator()));
        trie.put("word", 5);
        trie.put("word", 4);
        System.out.println(trie.delete("word", 5));
        System.out.println(trie.getAllSorted("word", new intComparator()));

    }
    @Test
    public void documentStoreTest() throws URISyntaxException, IOException {
        URI validUri = new URI("src/bru.txt");
        File poem = new File("src/bru.txt");
        InputStream validInput = new FileInputStream(poem);
        DocumentStoreImpl store = new DocumentStoreImpl();
        store.put(validInput, validUri, DocumentStore.DocumentFormat.TXT);
        Document doc = store.get(validUri);
        System.out.println(doc.getDocumentTxt());
        store.undo();
        assertNull(store.get(validUri));
        validInput = new FileInputStream(poem);
        store.put(validInput, validUri, DocumentStore.DocumentFormat.TXT);
        store.undo(validUri);
        assertNull(store.get(validUri));
        validInput = new FileInputStream(poem);
        store.put(validInput, validUri, DocumentStore.DocumentFormat.TXT);
        store.deleteAll("what");
        assertNull(store.get(validUri));
        store.undo();
        System.out.println(store.get(validUri).getDocumentTxt());
        store.deleteAllWithPrefix("wh");
        assertNull(store.get(validUri));
        store.undo();
        assertTrue(store.get(validUri) != null);
        URI uri = new URI("src/bru2.0.txt");
        File poem2 = new File("src/bru2.0.txt");
        InputStream input = new FileInputStream(poem2);
        store.put(input, uri, DocumentStore.DocumentFormat.TXT);
        store.deleteAllWithPrefix("I");
        assertNull(store.get(uri));
        assertNull(store.get(validUri));
        store.undo();
        assertTrue(store.get(uri) != null);
        assertTrue(store.get(validUri) != null);
        store.deleteAll("I");
        store.undo(uri);
        assertTrue(store.get(uri) != null);
        assertTrue(store.get(validUri) != null);
        store.delete(validUri);
        store.deleteAllWithPrefix("I");
        store.undo(validUri);
        assertTrue(store.get(validUri) != null);
        store.put(null, validUri, DocumentStore.DocumentFormat.TXT);
        store.undo(uri);
        assertTrue(store.get(uri) != null);
        assertNull(store.get(validUri));
        store.undo(validUri);
        assertTrue(store.get(validUri) != null);
    }
    @Test
    public void undoTest() throws URISyntaxException, IOException{
        URI validUri = new URI("src/bru.txt");
        File poem = new File("src/bru.txt");
        InputStream validInput = new FileInputStream(poem);
        DocumentStoreImpl store = new DocumentStoreImpl();
        URI uri = new URI("src/bru2.0.txt");
        File poem2 = new File("src/bru2.0.txt");
        InputStream input = new FileInputStream(poem2);
        store.put(validInput, validUri, DocumentStore.DocumentFormat.TXT);
        store.put(input, uri, DocumentStore.DocumentFormat.TXT);
        assertEquals(2, store.search("I").size());
        store.undo(validUri);
        assertNull(store.get(validUri));
        List<Document> list = store.searchByPrefix("what");
        assertTrue(list.isEmpty());
        list = store.search("what");
        assertTrue(list.isEmpty());
        assert(store.get(uri).getKey() == uri);
        assert(store.search("I").size() == 1);
    }

}

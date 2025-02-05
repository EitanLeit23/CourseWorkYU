package edu.yu.cs.com1320.project.stage5.impl;
import edu.yu.cs.com1320.project.*;

import java.io.File;
import java.util.*;
import java.util.function.Function;

import edu.yu.cs.com1320.project.Undoable;
import edu.yu.cs.com1320.project.impl.BTreeImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
//import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage5.Document;
import edu.yu.cs.com1320.project.stage5.DocumentStore;
import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import static java.lang.System.nanoTime;

public class DocumentStoreImpl implements DocumentStore{
    private BTreeImpl<URI, Document> store;
    private StackImpl<Undoable> undoStack;
    private TrieImpl<URI> trie;
    private MinHeapImpl<MinHeapNode> mManager;
    private int docLimit = -1;
    private int mLimit = -1;
    private int byteCount = 0;
    private int docCount = 0;
    /**
     * the two document formats supported by this document store.
     * Note that TXT means plain text, i.e. a String.
     */
    private class MinHeapNode implements Comparable<MinHeapNode>{
        URI uri;
        BTreeImpl<URI, Document> bTree;
        private MinHeapNode(URI uri, BTreeImpl<URI, Document> bTree){
            this.bTree = bTree;
            this.uri = uri;
        }
        private URI getUri(){
            return this.uri;
        }
        private BTreeImpl<URI, Document> getBTree(){
            return this.bTree;
        }

        @Override
        public int compareTo(MinHeapNode o) {
            if(o.getBTree().get(o.getUri()) == null){
                return 1;
            }
            return this.bTree.get(uri).compareTo(o.getBTree().get(o.getUri()));
        }
    }
    public DocumentStoreImpl(){
        this.store = new BTreeImpl<>();
        this.undoStack = new StackImpl<>();
        this.trie = new TrieImpl<>();
        this.mManager = new MinHeapImpl<>();
        this.store.setPersistenceManager(new DocumentPersistenceManager(null));
    }
    public DocumentStoreImpl(File baseDir){
        this.store = new BTreeImpl<>();
        this.store = new BTreeImpl<>();
        this.undoStack = new StackImpl<>();
        this.trie = new TrieImpl<>();
        this.mManager = new MinHeapImpl<>();
        DocumentPersistenceManager pm = new DocumentPersistenceManager(baseDir);
        this.store.setPersistenceManager(pm);
    }

    /**
     * @param input the document being put
     * @param uri unique identifier for the document
     * @param format indicates which type of document format is being passed
     * @return if there is no previous doc at the given URI, return 0. If there is a previous doc, return the hashCode of the previous doc. If InputStream is null, this is a delete, and thus return either the hashCode of the deleted doc or 0 if there is no doc to delete.
     * @throws IOException if there is an issue reading input
     * @throws IllegalArgumentException if uri or format are null
     */
    @Override
    public int put(InputStream input, URI uri, DocumentStore.DocumentFormat format) throws IOException{
        if(uri == null || format == null){
            throw new IllegalArgumentException();
        }
        if(input == null){
            DocumentImpl docReturned = (DocumentImpl) this.store.put(uri, null);
            removeDocInTrie(docReturned);
            removeFromCount(docReturned);
            removeFromHeap(docReturned);
            Function<URI, Boolean> undo = (URI) -> {
                putDocInTrie(docReturned);
                this.store.put(uri, docReturned);
                addToCount(docReturned);
                if(docReturned != null){
                    docReturned.setLastUseTime(nanoTime());
                    putInHeap(docReturned);
                }
                return true;
            };
            GenericCommand<URI> c = new GenericCommand<>(uri, undo);
            this.undoStack.push(c);
            if(docReturned != null){return docReturned.hashCode();

            }
            else{
                return 0;
            }
        }
        try{
            byte[] inputtedBytes = input.readAllBytes();
            //input.close();

            if(format == DocumentStore.DocumentFormat.BINARY){
                return binaryPut(uri, inputtedBytes);
            }
            else{
                return txtPut(uri, inputtedBytes);
            }
        }
        catch (IOException e) {
            throw new IOException();
        }
    }

    private void putInHeap(DocumentImpl docReturned) {
        if(docReturned == null){
            return;
        }
        URI uri = docReturned.getKey();
        Set<MinHeapNode> helper = new HashSet<>();
        try{
            MinHeapNode currentNode = this.mManager.remove();
            while(currentNode != null) {
                if (currentNode.getUri().equals(uri)) {
                    break;
                }
                if(!currentNode.getUri().equals(uri)){
                    helper.add(currentNode);
                }
                try {
                    currentNode = this.mManager.remove();
                } catch (NoSuchElementException e) {
                    break;
                }
            }
            for(MinHeapNode putBack : helper){
                this.mManager.insert(putBack);
            }
            if(currentNode != null){
                this.mManager.insert(currentNode);
            }
            /*if(currentNode != null){
                this.mManager.insert(currentNode);
            }
            else{
                MinHeapNode newNode = new MinHeapNode(uri, this.store);
                this.mManager.insert(newNode);
            }*/
        }catch(NoSuchElementException e){
            MinHeapNode newNode = new MinHeapNode(uri, this.store);
            this.mManager.insert(newNode);
            return;
        }

    }

    private void removeFromHeap(DocumentImpl docReturned) {
        if(docReturned == null){
            return;
        }
        docReturned.setLastUseTime(-1);
        URI uri = docReturned.getKey();
        MinHeapImpl<MinHeapNode> helper = new MinHeapImpl<>();
        try{
            MinHeapNode currentNode = this.mManager.remove();
            while(currentNode != null){
                if(currentNode.getUri().equals(uri)){
                    break;
                }
                helper.insert(currentNode);
                try{
                    currentNode = this.mManager.remove();
                } catch (NoSuchElementException e){
                    break;
                }
            }
            try{
                MinHeapNode putBack = helper.remove();
                while(putBack != null){
                    mManager.insert(putBack);
                    putBack = helper.remove();
                }
            } catch (NoSuchElementException e){
                return;
            }
        } catch(NoSuchElementException e){
            return;
        }

    }

    private void removeFromCount(DocumentImpl docReturned) {
        if(docReturned == null){
            return;
        }
        if(this.byteCount > 0){
            if(docReturned.getDocumentTxt() != null){
                this.byteCount = this.byteCount - docReturned.getDocumentTxt().getBytes(StandardCharsets.UTF_8).length;
            }
            if(docReturned.getDocumentBinaryData() != null){
                this.byteCount = this.byteCount - docReturned.getDocumentBinaryData().length;
            }
        }
        if(this.docCount > 0){
            this.docCount--;
        }
    }

    private void addToCount(DocumentImpl document){
        if(document == null){
            return;
        }
        if(document.getDocumentTxt() != null){
            this.docCount++;
            this.byteCount = this.byteCount + document.getDocumentTxt().getBytes(StandardCharsets.UTF_8).length;
        }
        if(document.getDocumentBinaryData() != null){
            this.docCount++;
            this.byteCount = this.byteCount + document.getDocumentBinaryData().length;
        }
    }

    private int txtPut(URI uri, byte[] inputtedBytes) throws IOException {
        String docStr = new String(inputtedBytes, StandardCharsets.UTF_8);
        DocumentImpl document = new DocumentImpl(uri, docStr, null);
        int mRequired = getMemoryAmount(document);
        DocumentImpl returnedDoc = (DocumentImpl) store.put(uri, document);
        manageMemory(mRequired, uri);
        putDocInTrie(document);
        if(returnedDoc != null){
            removeFromHeap(returnedDoc);
        }
        putInHeap(document);
        addToCount(document);
        removeFromCount(returnedDoc);
        removeDocInTrie(returnedDoc);
        Function<URI, Boolean> undo = (URI) -> {
            if(returnedDoc != null){
                try {
                    manageMemory(getMemoryAmount(returnedDoc), returnedDoc.getKey());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            this.store.put(uri, returnedDoc);
            removeDocInTrie(document);
            putDocInTrie(returnedDoc);
            removeFromCount(document);
            addToCount(returnedDoc);
            removeFromHeap(document);
            if(returnedDoc != null){
                returnedDoc.setLastUseTime(nanoTime());
                putInHeap(returnedDoc);
            }
            return true;
        };
        GenericCommand<URI> gc1 = new GenericCommand<>(uri, undo);
        this.undoStack.push(gc1);
        if(returnedDoc != null){
            return returnedDoc.hashCode();
        }
        else{
            return 0;
        }
    }

    private void manageMemory(int mRequired, URI uri) throws IOException {
        if(mLimit > -1) {
            if(mRequired > mLimit){
                this.store.moveToDisk(uri);
                return;
            }
            while (this.byteCount + mRequired > mLimit) {
                makeSpace();
            }
        }
        if(docLimit > -1) {
            if(1 > docLimit){
                this.store.moveToDisk(uri);
                return;
            }
            while (this.docCount + 1 > docLimit) {
                makeSpace();
            }
        }
    }

    private void makeSpace() throws IOException{
        DocumentImpl doc = (DocumentImpl) store.get(mManager.remove().getUri());
        removeFromCount(doc);
        this.store.moveToDisk(doc.getKey());
    }

    private void removeFromUndoStack(DocumentImpl doc) {
        URI uri = doc.getKey();
        StackImpl<Undoable> helper = new StackImpl<>();
        while(undoStack.peek() != null){
            Undoable temp = undoStack.pop();
            if(temp instanceof GenericCommand<?>){
                if(!((GenericCommand<?>) temp).getTarget().equals(uri)){
                    helper.push(temp);
                }
            }
            if(temp instanceof CommandSet<?>){
                if(!((CommandSet) temp).containsTarget(uri)){
                    helper.push(temp);
                }
                else{
                    ((CommandSet<?>) temp).remove(uri);
                    helper.push(temp);
                }
            }
        }
        while(helper.peek() != null){
            this.undoStack.push(helper.pop());
        }
    }

    private int getMemoryAmount(DocumentImpl document) {
        if(document.getDocumentTxt() != null){
            return document.getDocumentTxt().getBytes(StandardCharsets.UTF_8).length;
        }
        else{
            return document.getDocumentBinaryData().length;
        }
    }

    private int binaryPut(URI uri, byte[] inputtedBytes) throws IOException {
        DocumentImpl document = new DocumentImpl(uri, inputtedBytes);
        manageMemory(getMemoryAmount(document), uri);
        DocumentImpl returnedDoc = (DocumentImpl) store.put(uri, document);
        putInHeap(returnedDoc);
        addToCount(document);
        removeFromCount(returnedDoc);
        if(returnedDoc != null){
            removeFromHeap(returnedDoc);
        }
        Function<URI, Boolean> undo = (URI) -> {
            if(returnedDoc != null){
                try {
                    manageMemory(getMemoryAmount(returnedDoc), returnedDoc.getKey());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            this.store.put(uri, returnedDoc);
            addToCount(returnedDoc);
            removeFromCount(document);
            removeFromHeap(document);
            if(returnedDoc != null){
                returnedDoc.setLastUseTime(nanoTime());
                putInHeap(returnedDoc);
            }
            return true;
        };
        GenericCommand<URI> c = new GenericCommand<>(uri, undo);
        this.undoStack.push(c);
        if(returnedDoc != null){
            return returnedDoc.hashCode();
        }
        else{
            return 0;
        }
    }

    private void removeDocInTrie(DocumentImpl returnedDoc) {
        if(returnedDoc == null){
            return;
        }
        Set<String> words = returnedDoc.getWords();
        for(String word : words){
            this.trie.delete(word, returnedDoc.getKey());
        }

    }

    private void putDocInTrie(DocumentImpl document) {
        if(document == null){
            return;
        }
        Set<String> words = document.getWords();
        for(String str : words){
            this.trie.put(str, document.getKey());
        }
    }

    /**
     * @param uri the unique identifier of the document to get
     * @return the given document
     */
    @Override
    public Document get(URI uri){
        Document toReturn = this.store.get(uri);
        if(toReturn != null){
            toReturn.setLastUseTime(nanoTime());
            putInHeap((DocumentImpl)toReturn);
            if((docCount < docLimit && mLimit < 0) || (byteCount < mLimit && docLimit < 0)){
                addToCount((DocumentImpl) toReturn);
            }
            try {
                manageMemory(getMemoryAmount((DocumentImpl)toReturn), toReturn.getKey());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return toReturn;
    }

    /**
     * @param uri the unique identifier of the document to delete
     * @return true if the document is deleted, false if no document exists with that URI
     */
    @Override
    public boolean delete(URI uri){
        DocumentImpl docReturned = (DocumentImpl) this.store.get(uri);
        assert(docReturned != null);
        removeDocInTrie(docReturned);
        removeFromHeap(docReturned);
        removeFromCount(docReturned);
        this.store.put(uri, null);
        Function<URI, Boolean> undo = (URI) -> {
            putDocInTrie(docReturned);
            this.store.put(uri, docReturned);
            addToCount(docReturned);
            if(docReturned != null){
                docReturned.setLastUseTime(nanoTime());
                putInHeap(docReturned);
                try {
                    manageMemory(getMemoryAmount(docReturned), docReturned.getKey());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return true;
        };
        GenericCommand<URI> c = new GenericCommand<>(uri, undo);
        this.undoStack.push(c);
        if(docReturned == null){
            return false;
        }
        else{
            return true;
        }
    }

    @Override
    public void undo() throws IllegalStateException {
        Undoable toUndo = this.undoStack.pop();
        if(toUndo == null){
            throw new IllegalStateException();
        }
        toUndo.undo();
    }

    @Override
    public void undo(URI uri) throws IllegalStateException{
        boolean found = false;
        StackImpl<Undoable> helper = new StackImpl<>();
        while(this.undoStack.peek() != null){
            Undoable temp = this.undoStack.pop();
            if(temp instanceof GenericCommand<?>){
                GenericCommand tempTyped = (GenericCommand<?>)temp;
                if(tempTyped.getTarget().equals(uri)){
                    temp.undo();
                    found = true;
                    break;
                }
            }
            if(temp instanceof CommandSet<?>){
                CommandSet tempTyped = (CommandSet<?>)temp;
                if(tempTyped.containsTarget(uri)){
                    temp.undo();
                    found = true;
                    break;
                }
            }
            helper.push(temp);
        }
        if(!found){
            throw new IllegalStateException();
        }
        while(helper.peek() != null){
            Undoable temp = helper.pop();
            this.undoStack.push(temp);
        }
    }
    /**
     * Retrieve all documents whose text contains the given keyword.
     * Documents are returned in sorted, descending order, sorted by the number of times the keyword appears in the document.
     * Search is CASE SENSITIVE.
     * @param keyword
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    public List<Document> search(String keyword){
        List<URI> uriList = this.trie.getAllSorted(keyword, new Comparer(keyword, this.store));
        List<Document> toReturn = new ArrayList<>();
        long time = nanoTime();
        for(URI uri : uriList){
            toReturn.add(this.store.get(uri));
        }
        for(Document doc : toReturn){
            try {
                manageMemory(getMemoryAmount((DocumentImpl)doc), doc.getKey());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            addToCount((DocumentImpl) doc);
            doc.setLastUseTime(time);
            putInHeap((DocumentImpl)doc);
        }
        return toReturn;
    }

    /**
     * Retrieve all documents whose text starts with the given prefix
     * Documents are returned in sorted, descending order, sorted by the number of times the prefix appears in the document.
     * Search is CASE SENSITIVE.
     * @param keywordPrefix
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    public List<Document> searchByPrefix(String keywordPrefix){
        List<URI> uriList = this.trie.getAllWithPrefixSorted(keywordPrefix, new Comparer(keywordPrefix, this.store));
        long time = nanoTime();
        List<Document> toReturn = new ArrayList<>();
        for(URI uri : uriList){
            toReturn.add(this.store.get(uri));
        }
        for(Document doc : toReturn){
            try {
                manageMemory(getMemoryAmount((DocumentImpl) doc), doc.getKey());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            doc.setLastUseTime(time);
            addToCount((DocumentImpl) doc);
        }
        return toReturn;
    }

    /**
     * Completely remove any trace of any document which contains the given keyword
     * Search is CASE SENSITIVE.
     * @param keyword
     * @return a Set of URIs of the documents that were deleted.
     */
    public Set<URI> deleteAll(String keyword){
        Set<URI> deletedURI = this.trie.deleteAll(keyword);
        CommandSet<URI> commands = new CommandSet<>();
        for(URI uri : deletedURI){
            DocumentImpl doc = (DocumentImpl) this.store.get(uri);
            assert(doc != null);
            removeDocInTrie(doc);
            removeFromHeap(doc);
            removeFromCount(doc);
            this.store.put(uri, null);
            deletedURI.add(uri);
            Function<URI, Boolean> undo = (URI) -> {
                long time = nanoTime();
                this.store.put(uri, doc);
                if (doc != null) {
                    try {
                        manageMemory(getMemoryAmount((DocumentImpl)doc), doc.getKey());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                putDocInTrie(doc);
                doc.setLastUseTime(time);
                putInHeap(doc);
                addToCount(doc);
                return true;
            };
            GenericCommand<URI> c = new GenericCommand<>(uri, undo);
            commands.addCommand(c);
        }
        undoStack.push(commands);
        return deletedURI;
    }

    /**
     * Completely remove any trace of any document which contains a word that has the given prefix
     * Search is CASE SENSITIVE.
     * @param keywordPrefix
     * @return a Set of URIs of the documents that were deleted.
     */
    public Set<URI> deleteAllWithPrefix(String keywordPrefix){
        Set<URI> deletedURI = this.trie.deleteAllWithPrefix(keywordPrefix);
        CommandSet<URI> commands = new CommandSet<>();
        for(URI uri : deletedURI){
            DocumentImpl doc = (DocumentImpl) this.store.get(uri);
            assert(doc != null);
            removeFromHeap(doc);
            removeDocInTrie(doc);
            removeFromCount(doc);
            this.store.put(uri, null);
            //deletedURI.add(uri);
            Function<URI, Boolean> undo = (URI) -> {
                long time = nanoTime();
                this.store.put(uri, doc);
                if(doc != null){
                    try {
                        manageMemory(getMemoryAmount(doc), doc.getKey());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                addToCount(doc);
                putDocInTrie(doc);
                doc.setLastUseTime(time);
                putInHeap(doc);
                return true;
            };
            GenericCommand<URI> c = new GenericCommand<>(uri, undo);
            commands.addCommand(c);
        }
        undoStack.push(commands);
        return deletedURI;
    }

    @Override
    public void setMaxDocumentCount(int limit) {
        if(limit < 0){
            throw new IllegalArgumentException();
        }
        this.docLimit = limit;
        while(this.docCount > this.docLimit){
            try {
                makeSpace();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void setMaxDocumentBytes(int limit) {
        if(limit < 0){
            throw new IllegalArgumentException();
        }
        this.mLimit = limit;
        while(this.byteCount > this.mLimit){
            try {
                makeSpace();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class Comparer implements Comparator<URI>{
        private String word;
        private BTreeImpl<URI, Document> bTree;
        private Comparer(String word, BTreeImpl<URI, Document> bTree){
            this.word = word;
            this.bTree = bTree;
        }
        @Override
        public int compare(URI uri1 , URI uri2) {
            DocumentImpl doc1 = (DocumentImpl) this.bTree.get(uri1);
            DocumentImpl doc2 = (DocumentImpl) this.bTree.get(uri2);
            return -1 * Integer.compare(doc1.wordCount(word), doc2.wordCount(word));
        }
    }
}

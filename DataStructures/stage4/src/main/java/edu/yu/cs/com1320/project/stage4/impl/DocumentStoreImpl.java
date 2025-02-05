package edu.yu.cs.com1320.project.stage4.impl;
import edu.yu.cs.com1320.project.CommandSet;
import edu.yu.cs.com1320.project.GenericCommand;
import edu.yu.cs.com1320.project.Undoable;

import java.util.*;
import java.util.function.Function;

import edu.yu.cs.com1320.project.Undoable;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage4.Document;
import edu.yu.cs.com1320.project.stage4.DocumentStore;
import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import static java.lang.System.nanoTime;

public class DocumentStoreImpl implements DocumentStore{
    HashTableImpl<URI, DocumentImpl> store;
    private StackImpl<Undoable> undoStack;
    private TrieImpl<Document> trie;
    private MinHeapImpl<Document> mManager;
    private int docLimit = -1;
    private int mLimit = -1;
    private int byteCount = 0;
    private int docCount = 0;
    /**
     * the two document formats supported by this document store.
     * Note that TXT means plain text, i.e. a String.
     */
    public DocumentStoreImpl(){
        this.store = new HashTableImpl<>();
        this.undoStack = new StackImpl<>();
        this.trie = new TrieImpl<>();
        this.mManager = new MinHeapImpl<>();
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
            DocumentImpl docReturned = this.store.put(uri, null);
            removeDocInTrie(docReturned);
            removeFromCount(docReturned);
            removeFromHeap(docReturned);
            Function<URI, Boolean> undo = (URI) -> {
                putDocInTrie(docReturned);
                this.store.put(uri, docReturned);
                addToCount(docReturned);
                if(docReturned != null){
                    this.mManager.insert(docReturned);
                    docReturned.setLastUseTime(nanoTime());
                    this.mManager.reHeapify(docReturned);
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

    private void removeFromHeap(DocumentImpl docReturned) {
        docReturned.setLastUseTime(-1);
        this.mManager.reHeapify(docReturned);
        this.mManager.remove();
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

    private int txtPut(URI uri, byte[] inputtedBytes){
        String docStr = new String(inputtedBytes, StandardCharsets.UTF_8);
        DocumentImpl document = new DocumentImpl(uri, docStr);
        int mRequired = getMemoryAmount(document);
        manageMemory(mRequired);
        putDocInTrie(document);
        DocumentImpl returnedDoc = store.put(uri, document);
        this.mManager.insert(document);
        addToCount(document);
        removeFromCount(returnedDoc);
        removeDocInTrie(returnedDoc);
        if(returnedDoc != null){
            removeFromHeap(returnedDoc);
        }
        Function<URI, Boolean> undo = (URI) -> {
            if(returnedDoc != null){
                manageMemory(getMemoryAmount(returnedDoc));
            }
            this.store.put(uri, returnedDoc);
            removeDocInTrie(document);
            putDocInTrie(returnedDoc);
            removeFromCount(document);
            addToCount(returnedDoc);
            removeFromHeap(document);
            if(returnedDoc != null){
                this.mManager.insert(returnedDoc);
                returnedDoc.setLastUseTime(nanoTime());
                this.mManager.reHeapify(returnedDoc);
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

    private void manageMemory(int mRequired) {
        if(mLimit > -1) {
            if(mRequired > mLimit){
                throw new IllegalArgumentException();
            }
            while (this.byteCount + mRequired > mLimit) {
                makeSpace();
            }
        }
        if(docLimit > -1) {
            while (this.docCount + 1 > docLimit) {
                makeSpace();
            }
        }
    }

    private void makeSpace() {
        DocumentImpl doc = (DocumentImpl) mManager.remove();
        removeFromCount(doc);
        this.store.put(doc.getKey(), null);
        removeDocInTrie(doc);
        removeFromUndoStack(doc);
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

    private int binaryPut(URI uri, byte[] inputtedBytes) {
        DocumentImpl document = new DocumentImpl(uri, inputtedBytes);
        manageMemory(getMemoryAmount(document));
        DocumentImpl returnedDoc = store.put(uri, document);
        this.mManager.insert(document);
        addToCount(document);
        removeFromCount(returnedDoc);
        if(returnedDoc != null){
            removeFromHeap(returnedDoc);
        }
        Function<URI, Boolean> undo = (URI) -> {
            if(returnedDoc != null){
                manageMemory(getMemoryAmount(returnedDoc));
            }
            this.store.put(uri, returnedDoc);
            addToCount(returnedDoc);
            removeFromCount(document);
            removeFromHeap(document);
            if(returnedDoc != null){
                this.mManager.insert(returnedDoc);
                returnedDoc.setLastUseTime(nanoTime());
                this.mManager.reHeapify(returnedDoc);
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
            this.trie.delete(word, returnedDoc);
        }

    }

    private void putDocInTrie(DocumentImpl document) {
        if(document == null){
            return;
        }
        Set<String> words = document.getWords();
        for(String str : words){
            this.trie.put(str, document);
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
            this.mManager.reHeapify(toReturn);
        }
        return toReturn;
    }

    /**
     * @param uri the unique identifier of the document to delete
     * @return true if the document is deleted, false if no document exists with that URI
     */
    @Override
    public boolean delete(URI uri){
        DocumentImpl docReturned = this.store.put(uri, null);
        removeDocInTrie(docReturned);
        removeFromHeap(docReturned);
        removeFromCount(docReturned);
        Function<URI, Boolean> undo = (URI) -> {
            if(docReturned != null){
                manageMemory(getMemoryAmount(docReturned));
            }
            putDocInTrie(docReturned);
            this.store.put(uri, docReturned);
            addToCount(docReturned);
            if(docReturned != null){
                docReturned.setLastUseTime(nanoTime());
                this.mManager.insert(docReturned);
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
        List<Document> toReturn = this.trie.getAllSorted(keyword, new Comparer(keyword));
        long time = nanoTime();
        for(Document doc : toReturn){
            doc.setLastUseTime(time);
            this.mManager.reHeapify(doc);
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
        List<Document> toReturn = this.trie.getAllWithPrefixSorted(keywordPrefix, new Comparer(keywordPrefix));
        long time = nanoTime();
        for(Document doc : toReturn){
            doc.setLastUseTime(time);
            this.mManager.reHeapify(doc);
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
        Set<URI> deletedURI = new HashSet<>();
        Set<Document> deletedDocs = this.trie.deleteAll(keyword);
        CommandSet<URI> commands = new CommandSet<>();
        for(Document doc : deletedDocs){
            URI uri = doc.getKey();
            this.store.put(uri, null);
            removeDocInTrie((DocumentImpl)doc);
            removeFromHeap((DocumentImpl)doc);
            removeFromCount((DocumentImpl)doc);
            deletedURI.add(uri);
            Function<URI, Boolean> undo = (URI) -> {
                long time = nanoTime();
                if (doc != null) {
                    manageMemory(getMemoryAmount((DocumentImpl)doc));
                }
                DocumentImpl docTyped = (DocumentImpl)doc;
                putDocInTrie(docTyped);
                doc.setLastUseTime(time);
                this.mManager.insert(doc);
                this.store.put(uri, docTyped);
                addToCount((DocumentImpl)doc);
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
        Set<URI> deletedURI = new HashSet<>();
        Set<Document> deletedDocs = this.trie.deleteAllWithPrefix(keywordPrefix);
        CommandSet<URI> commands = new CommandSet<>();
        for(Document doc : deletedDocs){
            URI uri = doc.getKey();
            this.store.put(uri, null);
            removeDocInTrie((DocumentImpl)doc);
            removeFromHeap((DocumentImpl)doc);
            removeFromCount((DocumentImpl)doc);
            deletedURI.add(uri);
            Function<URI, Boolean> undo = (URI) -> {
                long time = nanoTime();
                DocumentImpl docTyped = (DocumentImpl)doc;
                if(doc != null){
                    manageMemory(getMemoryAmount(docTyped));
                }
                this.store.put(uri, docTyped);
                addToCount((DocumentImpl)doc);
                putDocInTrie(docTyped);
                doc.setLastUseTime(time);
                this.mManager.insert(doc);
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
            makeSpace();
        }
    }

    @Override
    public void setMaxDocumentBytes(int limit) {
        if(limit < 0){
            throw new IllegalArgumentException();
        }
        this.mLimit = limit;
        while(this.byteCount > this.mLimit){
            makeSpace();
        }
    }

    private class Comparer implements Comparator<Document>{
        private String word;
        private Comparer(String word){
            this.word = word;
        }
        @Override
        public int compare(Document doc1, Document doc2) {
            return -1 * Integer.compare(doc1.wordCount(word), doc2.wordCount(word));
        }
    }
}

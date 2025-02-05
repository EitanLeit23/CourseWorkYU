package edu.yu.cs.com1320.project.stage3.impl;
import edu.yu.cs.com1320.project.CommandSet;
import edu.yu.cs.com1320.project.GenericCommand;
import edu.yu.cs.com1320.project.Undoable;

import java.util.*;
import java.util.function.Function;

import edu.yu.cs.com1320.project.Undoable;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage3.Document;
import edu.yu.cs.com1320.project.stage3.DocumentStore;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class DocumentStoreImpl implements DocumentStore{
    HashTableImpl<URI, DocumentImpl> store;
    private StackImpl<Undoable> undoStack;
    private TrieImpl<Document> trie;
    /**
     * the two document formats supported by this document store.
     * Note that TXT means plain text, i.e. a String.
     */
    public DocumentStoreImpl(){
        this.store = new HashTableImpl<>();
        this.undoStack = new StackImpl<>();
        this.trie = new TrieImpl<>();
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
            Function<URI, Boolean> undo = (URI) -> {
                putDocInTrie(docReturned);
                this.store.put(uri, docReturned);
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

    private int txtPut(URI uri, byte[] inputtedBytes) {
        String docStr = new String(inputtedBytes, StandardCharsets.UTF_8);
        DocumentImpl document = new DocumentImpl(uri, docStr);
        putDocInTrie(document);
        DocumentImpl returnedDoc = store.put(uri, document);
        removeDocInTrie(returnedDoc);
        Function<URI, Boolean> undo = (URI) -> {
            this.store.put(uri, returnedDoc);
            removeDocInTrie(document);
            putDocInTrie(returnedDoc);
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

    private int binaryPut(URI uri, byte[] inputtedBytes) {
        DocumentImpl document = new DocumentImpl(uri, inputtedBytes);
        DocumentImpl returnedDoc = store.put(uri, document);
        Function<URI, Boolean> undo = (URI) -> {
            this.store.put(uri, returnedDoc);
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
        return this.store.get(uri);
    }

    /**
     * @param uri the unique identifier of the document to delete
     * @return true if the document is deleted, false if no document exists with that URI
     */
    @Override
    public boolean delete(URI uri){
        DocumentImpl docReturned = this.store.put(uri, null);
        removeDocInTrie(docReturned);
        Function<URI, Boolean> undo = (URI) -> {
            putDocInTrie(docReturned);
            this.store.put(uri, docReturned);
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
            deletedURI.add(uri);
            Function<URI, Boolean> undo = (URI) -> {
                DocumentImpl docTyped = (DocumentImpl)doc;
                putDocInTrie(docTyped);
                this.store.put(uri, docTyped);
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
        for(Document doc1 : deletedDocs){
            removeDocInTrie((DocumentImpl)doc1);

            /*Set<String> words = doc1.getWords();
            for(String word : words){
                List<Document> documents = this.trie.getAllSorted(word, new Comparer(word));
                for(Document doc2 : documents){
                    if(doc2.equals(doc1)){
                        this.trie.delete(word, doc1);
                    }
                }
            } */
        }
        CommandSet<URI> commands = new CommandSet<>();
        for(Document doc : deletedDocs){
            URI uri = doc.getKey();
            this.store.put(uri, null);
            deletedURI.add(uri);
            Function<URI, Boolean> undo = (URI) -> {
                DocumentImpl docTyped = (DocumentImpl)doc;
                this.store.put(uri, docTyped);
                putDocInTrie(docTyped);
                return true;
            };
            GenericCommand<URI> c = new GenericCommand<>(uri, undo);
            commands.addCommand(c);
        }
        undoStack.push(commands);
        return deletedURI;
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

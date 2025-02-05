package edu.yu.cs.com1320.project.stage1.impl;
import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.stage1.Document;
import edu.yu.cs.com1320.project.stage1.DocumentStore;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class DocumentStoreImpl implements DocumentStore{
    HashTableImpl<URI, DocumentImpl> store;
    /**
     * the two document formats supported by this document store.
     * Note that TXT means plain text, i.e. a String.
     */
    public DocumentStoreImpl(){
        this.store = new HashTableImpl<>();
    }
    enum DocumentFormat{
        TXT,BINARY
    };
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
            if(docReturned != null){
                return docReturned.hashCode();
            }
            else{
                return 0;
            }
        }
        try{
            byte[] inputtedBytes = input.readAllBytes();
            //input.close();
            if(format == DocumentStore.DocumentFormat.BINARY){
                DocumentImpl document = new DocumentImpl(uri, inputtedBytes);
                DocumentImpl returnedDoc = store.put(uri, document);
                if(returnedDoc != null){
                    return returnedDoc.hashCode();
                }
                else{
                    return 0;
                }
            }
            else{
                String docStr = new String(inputtedBytes, StandardCharsets.UTF_8);
                DocumentImpl document = new DocumentImpl(uri, docStr);
                DocumentImpl returnedDoc = store.put(uri, document);
                if(returnedDoc != null){
                    return returnedDoc.hashCode();
                }
                else{
                    return 0;
                }
            }
        }
        catch (IOException e) {
            throw new IOException();
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
        if(docReturned == null){
            return false;
        }
        else{
            return true;
        }
    }
}

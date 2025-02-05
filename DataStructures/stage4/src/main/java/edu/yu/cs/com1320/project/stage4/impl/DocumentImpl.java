package edu.yu.cs.com1320.project.stage4.impl;
import edu.yu.cs.com1320.project.stage4.Document;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.System.nanoTime;

public class DocumentImpl implements Document{
    private URI uri;
    private String txt = null;
    private byte[] binaryData = null;
    private long useTime;
    private Hashtable<String, Integer> wordCountMap = new Hashtable<>();
    public DocumentImpl(URI uri, String txt){
        if(uri == null || txt == null || uri.toString().isBlank() || txt.isBlank()){
            throw new IllegalArgumentException();
        }
        this.uri = uri;
        this.txt = txt;
        this.useTime = nanoTime();
        breakTxtIntoCleanStrings();
    }

    private void breakTxtIntoCleanStrings() {
        String cleanTxt = this.txt;
                cleanTxt.replaceAll(
                "[^a-zA-Z0-9]", " ");
        String[] allClean = cleanTxt.split(" ");
        for(int i = 0; i < allClean.length; i++){
            String temp = allClean[i];
            if(this.wordCountMap.get(temp) == null){
                this.wordCountMap.put(temp, 1);
            }
            else{
                int count = this.wordCountMap.get(temp);
                this.wordCountMap.put(temp, count + 1);
            }
        }
    }

    public DocumentImpl(URI uri, byte[] binaryData){
        if(uri == null || uri.toString().isBlank() || binaryData == null || binaryData.length == 0){
            throw new IllegalArgumentException();
        }
        boolean isBlank = true;
        for(byte b : binaryData){
            if(b != 0){
                isBlank = false;
                break;
            }
        }
        if(isBlank){
            throw new IllegalArgumentException();
        }
        this.uri = uri;
        this.binaryData = binaryData;
    }
    /**
     * @return content of text document
     */
    @Override
    public String getDocumentTxt(){
        return this.txt;
    }

    /**
     * @return content of binary data document
     */
    @Override
    public byte[] getDocumentBinaryData(){
        if(this.binaryData == null){
            return null;
        }
        else{
            return binaryData;
        }
    }

    /**
     * @return URI which uniquely identifies this document
     */
    @Override
    public URI getKey(){
        return this.uri;
    }
    @Override
    public int hashCode(){
        int result = uri.hashCode();
        result = 31 * result + (txt != null ? txt.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(binaryData);
        return result;
    }
    @Override
    public boolean equals(Object other){
        if(other == null){
            return false;
        }
        if(!(other instanceof DocumentImpl)){
            return false;
        }
        DocumentImpl otherTyped = (DocumentImpl)other;
        boolean toReturn = (this.hashCode() == otherTyped.hashCode());
        return toReturn;
    }
    /**
     * how many times does the given word appear in the document?
     * @param word
     * @return the number of times the given words appears in the document. If it's a binary document, return 0.
     */
    public int wordCount(String word) {
        if(binaryData != null){
            return 0;
        }
        if(this.wordCountMap.get(word) == null){
            return 0;
        }
        return this.wordCountMap.get(word);
    }

    /**
     * @return all the words that appear in the document
     */
    public Set<String> getWords(){
        Set<String> toReturn = new HashSet<>();
        if(this.txt == null){
            return toReturn;
        }
        for(Map.Entry<String, Integer> entry : this.wordCountMap.entrySet()){
            String toAdd = entry.getKey();
            toReturn.add(toAdd);
        }
        return toReturn;
    }

    @Override
    public long getLastUseTime() {
        return this.useTime;
    }

    @Override
    public void setLastUseTime(long timeInNanoseconds) {
        this.useTime = timeInNanoseconds;
    }

    @Override
    public int compareTo(Document doc) {
        if(doc == null){
            throw new NullPointerException();
        }
        return Long.compare(this.useTime, doc.getLastUseTime());
    }
}
package edu.yu.cs.com1320.project.stage2.impl;
import edu.yu.cs.com1320.project.stage2.Document;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DocumentImpl implements Document{
    private URI uri;
    private String txt = null;
    private byte[] binaryData = null;
    public DocumentImpl(URI uri, String txt){
        if(uri == null || txt == null || uri.toString().isBlank() || txt.isBlank()){
            throw new IllegalArgumentException();
        }
        this.uri = uri;
        this.txt = txt;
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
}
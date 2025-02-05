package edu.yu.cs.com1320.project.stage5.impl;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import com.google.gson.*;
import edu.yu.cs.com1320.project.stage5.Document;
import edu.yu.cs.com1320.project.stage5.PersistenceManager;
import jakarta.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * created by the document store and given to the BTree via a call to BTree.setPersistenceManager
 */
public class DocumentPersistenceManager implements PersistenceManager<URI, Document> {
   private File baseDir = null;
   private String separator = File.separator;
   private Gson gson = new GsonBuilder().registerTypeAdapter(DocumentImpl.class, new DocSerializer()).setPrettyPrinting().create();



    private class DocSerializer implements JsonSerializer<Document>, JsonDeserializer<Document>{
        @Override
        public JsonElement serialize(Document document, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("uri", document.getKey().toString());
            byte[] bytes = document.getDocumentBinaryData();
            String txt = document.getDocumentTxt();
            if(txt == null){
                jsonObject.addProperty("bytes", DatatypeConverter.printBase64Binary(bytes));
            }
            else{
                jsonObject.addProperty("txt", txt);
            }
            jsonObject.add("wordMap", context.serialize(document.getWordMap()));
            return jsonObject;
        }
        @Override
        public Document deserialize(JsonElement json, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            URI uri = URI.create(jsonObject.get("uri").getAsString());
            String txt = null;
            byte[] bytes = null;
            if(jsonObject.has("bytes")){
                bytes = DatatypeConverter.parseBase64Binary(jsonObject.get("bytes").getAsString());
            }
            else{
                txt = jsonObject.get("txt").getAsString();
            }
            Map<String, Integer> wordMap = jsonDeserializationContext.deserialize(jsonObject.get("wordMap"), Map.class);
            if(txt == null){
                return new DocumentImpl(uri, bytes);
            }
            else{
                return new DocumentImpl(uri, txt, wordMap);
            }

        }
    }
    public DocumentPersistenceManager(File baseDir){
        if(baseDir != null){
            this.baseDir = baseDir;
        }
    }

    @Override
    public void serialize(URI uri, Document val) throws IOException {
        if(uri == null || val == null){
            throw new IllegalArgumentException("null value for serialize");
        }
        /*String uriString = uri.toString();
        String[] array = uriString.split(this.seperator);
        if(this.baseDir != null){
            array[0] = this.baseDir.toString();
            String filepath = String.join(this.seperator, array);
            filepath += ".json";
            File file = new File(filepath);
            FileWriter fileWriter = new FileWriter(file);
            //gson.toJson(val, fileWriter);
            fileWriter.write(gson.toJson(val));
        }
        else{
            array[0] = System.getProperty("user.dir");
            String filepath = String.join(this.seperator, array);
            filepath += ".json";
            File file = new File(filepath);
            FileWriter fileWriter = new FileWriter(file);
            //gson.toJson(val, fileWriter);
            fileWriter.write(gson.toJson(val));
        } */
        String uriString = uri.toString();
        String[] array = uriString.split("/");
        String filePath;

        if (this.baseDir != null) {
            array[0] = this.baseDir.toString();
            filePath = String.join(File.separator, array);
        } else {
            array[0] = System.getProperty("user.dir");
            filePath = String.join(File.separator, array);
        }
        Path parentDirectory = Paths.get(filePath).getParent();
        //File file = new File(filePath);
        Files.createDirectories(parentDirectory);
        //file.mkdir();
        filePath += ".json";
        //file = new File(filePath);
        try (FileWriter fileWriter = new FileWriter(filePath)) {
            gson.toJson(val, fileWriter);
        }
    }

    @Override
    public Document deserialize(URI uri) throws IOException {
        if(uri == null){
            throw new IllegalArgumentException("deserialize null URI given");
        }
        /*String uriString = uri.toString();
        String[] array = uriString.split("/");
        if(this.baseDir != null){
            array[0] = this.baseDir.toString();
            String filepath = String.join("/", array);
            filepath += ".json";
            FileReader reader = new FileReader(filepath);
            return gson.fromJson(reader, Document.class);
        }
        else{
            array[0] = System.getProperty("user.dir");
            String filepath = String.join("/", array);
            filepath += ".json";
            FileReader reader = new FileReader(filepath);
            return gson.fromJson(reader, Document.class);
        }*/
        String uriString = uri.toString();
        String[] array = uriString.split("/");
        String filePath;

        if (this.baseDir != null) {
            array[0] = this.baseDir.toString();
            filePath = String.join(File.separator, array);
        } else {
            array[0] = System.getProperty("user.dir");
            filePath = String.join(File.separator, array);
        }
        Path parentDirectory = Paths.get(filePath).getParent();
        //File file = new File(filePath);
        Files.createDirectories(parentDirectory);
        //file.mkdir();
        filePath += ".json";
        //file = new File(filePath);

        try (FileReader reader = new FileReader(filePath)) {
            return gson.fromJson(reader, DocumentImpl.class);
        } catch (IOException | RuntimeException e) {
            throw e;
        }
    }

    @Override
    public boolean delete(URI uri) throws IOException {
        String uriString = uri.toString();
        String[] array = uriString.split("/");
        if(this.baseDir != null){
            array[0] = this.baseDir.toString();
            String filepath = String.join(File.separator, array);
            filepath += ".json";
            File file = new File(filepath);
            return file.delete();
        }
        else{
            array[0] = System.getProperty("user.dir");
            String filepath = String.join(File.separator, array);
            filepath += ".json";
            File file = new File(filepath);
            return file.delete();
        }
    }
}

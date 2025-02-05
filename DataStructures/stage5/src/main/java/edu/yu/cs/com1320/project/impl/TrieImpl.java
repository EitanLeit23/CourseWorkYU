package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Trie;

import java.util.*;

public class TrieImpl<Value> implements Trie<Value> {
    private static final int alphabetSize = 128; // extended ASCII
    private Node root; // root of trie
    private Set<Value> deletedSet;
    private boolean valDeleted;
    private List<Value> tempList = new ArrayList<>();
    public TrieImpl(){
        this.root = new Node();
        this.deletedSet = new HashSet<>();
    }

    private class Node<Value> {
        protected Set<Value> val = new HashSet<>();
        protected Node[] links = new Node[alphabetSize];
    }
    /**
     * add the given value at the given key
     * @param key
     * @param val
     */
    public void put(String key, Value val) {
        if(key == null){
            throw new IllegalArgumentException();
        }
        if (val == null){
            return;
        }
        //this.root = put(this.root, key, val, 0);
        Node current = this.root;
        for(int i = 0; i < key.length(); i++){
            if(current.links[key.charAt(i)] == null){
                current.links[key.charAt(i)] = new Node<Value>();
            }
            current = current.links[key.charAt(i)];
        }
        current.val.add(val);
    }
    private Node put(Node x, String key, Value val, int d)
    {
        //create a new node
        if (x == null)
        {
            x = new Node();
        }
        //we've reached the last node in the key,
        //set the value for the key and return the node
        if (d == key.length())
        {
            x.val.add(val);
            return x;
        }
        //proceed to the next node in the chain of nodes that
        //forms the desired key
        char c = key.charAt(d);
        x.links[c] = this.put(x.links[c], key, val, d + 1);
        return x;
    }


    /**
     * get all exact matches for the given key, sorted in descending order.
     * Search is CASE SENSITIVE.
     * @param key
     * @param comparator used to sort values
     * @return a List of matching Values, in descending order
     */
    public List<Value> getAllSorted(String key, Comparator<Value> comparator){
        Node current = this.root;
        if(comparator == null){
            throw new IllegalArgumentException();
        }
        for(int i = 0; i < key.length(); i++){
            if(current == null){
                return new ArrayList<>();
            }
            current = current.links[key.charAt(i)];
        }
        List<Value> toReturn = new ArrayList<>();
        if(current != null){
            toReturn.addAll(current.val);
        }
        toReturn.sort(comparator);
        //Collections.reverse(toReturn);
        return toReturn;
    }

    /**
     * get all matches which contain a String with the given prefix, sorted in descending order.
     * For example, if the key is "Too", you would return any value that contains "Tool", "Too", "Tooth", "Toodle", etc.
     * Search is CASE SENSITIVE.
     * @param prefix
     * @param comparator used to sort values
     * @return a List of all matching Values containing the given prefix, in descending order
     */
    public List<Value> getAllWithPrefixSorted(String prefix, Comparator<Value> comparator){
        Node current = this.root;
        if(comparator == null){
            throw new IllegalArgumentException();
        }
        List<Value> toReturn = new ArrayList<>();
        for(int i = 0; i < prefix.length(); i++){
            if(current == null){
                return new ArrayList<>();
            }
            current = current.links[prefix.charAt(i)];
        }
        if(current != null){
            toReturn.addAll(current.val);
        }
        addAllWithPrefix(current);
        toReturn.addAll(tempList);
        tempList = new ArrayList<>();
        toReturn.sort(comparator);
       // Collections.reverse(toReturn);
        return toReturn;
    }

    private void addAllWithPrefix(Node root) {
        if(root==null){
            return;
        }
        for(int i = 0; i < alphabetSize; i++){
            if(root.links[i] != null){
                tempList.addAll(root.links[i].val);
                addAllWithPrefix(root.links[i]);
            }
        }
    }

    /**
     * Delete the subtree rooted at the last character of the prefix.
     * Search is CASE SENSITIVE.
     * @param prefix
     * @return a Set of all Values that were deleted.
     */
    public Set<Value> deleteAllWithPrefix(String prefix){
        if(prefix == null){
            throw new IllegalArgumentException();
        }
        Node lastChar = this.root;
        for(int i = 0; i < prefix.length(); i++){
            if(lastChar.links[prefix.charAt(i)] != null){
            lastChar = lastChar.links[prefix.charAt(i)];
            }
            else{
                return new HashSet<>();
            }
        }
        this.deletedSet.addAll(lastChar.val);
        this.deleteSubtree(lastChar);
        lastChar = this.root;
        for(int i = 0; i < prefix.length()-1; i++) {
            if (lastChar.links[prefix.charAt(i)] != null) {
                lastChar = lastChar.links[prefix.charAt(i)];
            }
        }
        lastChar.links[prefix.charAt(prefix.length()-1)] = null;
        if(prefix.length() ==1){
            this.root.links[prefix.charAt(0)] = null;
        }
        Set<Value> toReturn = this.deletedSet;
        this.deletedSet = new HashSet<>();
        lastChar.links = new Node[alphabetSize];
        return toReturn;
    }

    private void deleteSubtree(Node root){
        for(int i = 0; i < alphabetSize; i++){
            if(root.links[i] != null){
                Set<Value> temp = root.links[i].val;
                this.deletedSet.addAll(temp);
                deleteSubtree(root.links[i]);

            }
        }
    }

    /**
     * Delete all values from the node of the given key (do not remove the values from other nodes in the Trie)
     * @param key
     * @return a Set of all Values that were deleted.
     */
    public Set<Value> deleteAll(String key){
        if(key == null){
            throw new IllegalArgumentException();
        }
        this.root = deleteAll(this.root, key, 0);
        Set<Value> returnSet = this.deletedSet;
        deletedSet = new HashSet<>();
        return returnSet;
    }
    private Node deleteAll(Node x, String key, int d) {
        if (x == null)
        {
            return null;
        }
        //we're at the node to del - set the val to null
        if (d == key.length()) {
            this.deletedSet = x.val;
            x.val = new HashSet<>();
        }
        //continue down the trie to the target node
        else {
            char c = key.charAt(d);
            x.links[c] = this.deleteAll(x.links[c], key, d + 1);
        }
        //this node has a val – do nothing, return the node
        if (!x.val.isEmpty()) {
            return x;
        }
        //remove subtrie rooted at x if it is completely empty
        for (int c = 0; c <alphabetSize; c++) {
            if (x.links[c] != null) {
                return x; //not empty
            }
        }
        //empty - set this link to null in the parent
        return null;
    }

    /**
     * Remove the given value from the node of the given key (do not remove the value from other nodes in the Trie)
     * @param key
     * @param val
     * @return the value which was deleted. If the key did not contain the given value, return null.
     */
    public Value delete(String key, Value val){
        //this.root = delete(this.root, key, val, 0);
        Node current = this.root;
        for(int i = 0; i < key.length(); i++){
            if(current.links[key.charAt(i)] == null){
                return null;
            }
            current = current.links[key.charAt(i)];
        }
        Value toReturn = null;
        for(Object value : current.val){
            if(value.equals(val)){
                toReturn = (Value)value;
            }
        }
        current.val.remove(toReturn);
        return toReturn;
    }
    private Node delete(Node x, String key, Value val, int d)
    {
        if (x == null)
        {
            return null;
        }
        //we're at the node to del - set the val to null
        if (d == key.length())
        {
            this.valDeleted = x.val.remove(val);
        }
        //continue down the trie to the target node
        else
        {
            char c = key.charAt(d);
            x.links[c] = this.deleteAll(x.links[c], key, d + 1);
        }
        //this node has a val – do nothing, return the node
        if (!x.val.isEmpty())
        {
            return x;
        }
        //remove subtrie rooted at x if it is completely empty
        for (int c = 0; c <alphabetSize; c++)
        {
            if (x.links[c] != null)
            {
                return x; //not empty
            }
        }
        //empty - set this link to null in the parent
        return null;
    }

}

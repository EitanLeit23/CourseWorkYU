package edu.yu.cs.com1320.project.impl;
import edu.yu.cs.com1320.project.HashTable;
public class HashTableImpl<Key, Value> implements HashTable<Key, Value>{
    private LinkedList<Key, Value>[] table;
    private int elementAmount;
    /**
     * Instances of HashTable should be constructed with two type parameters, one for the type of the keys in the table and one for the type of the values
     * @param <Key>
     * @param <Value>
     */
    public HashTableImpl(){
        this.table = new LinkedList[5];
        for(int i = 0; i < 5; i++){
        this.table[i] = new LinkedList<Key, Value>();
        }
    }
    /**
     * @param k the key whose value should be returned
     * @return the value that is stored in the HashTable for k, or null if there is no such key in the table
     */
    @Override
    public Value get(Key k){
        int ArrayElement = this.getHashCode(k);
        LinkedList<Key, Value> listToSearch = this.table[ArrayElement];
        Value v = listToSearch.searchValueReturn(k);
        return v;
    }

    /**
     * * @param k the key at which to store the value
     * @param v the value to store.
     * To delete an entry, put a null value.
     * @return if the key was already present in the HashTable, return the previous value stored for the key. If the key was not already present, return null.
     */
    @Override
    public Value put(Key k, Value v) {
        if(this.table.length <= elementAmount/4){
            this.resize();
        }
        int ArrayElement = this.getHashCode(k);
        LinkedList<Key, Value> listToPut = this.table[ArrayElement];
        Value toReturn = listToPut.add(k, v);
        if(v == null){
            elementAmount--;
        }
        else{
            elementAmount++;
        }
        return toReturn;
    }

    /**
     * @param key the key whose presence in the hashtabe we are inquiring about
     * @return true if the given key is present in the hashtable as a key, false if not
     * @throws NullPointerException if the specified key is null
     */
    @Override
    public boolean containsKey(Key key){
        if(key == null) {
            throw new NullPointerException();
        }
        int arrayElement = getHashCode(key);
        boolean contains = false;
        LinkedList<Key, Value> toSearch = this.table[arrayElement];
        return toSearch.searchValueReturn(key) != null;
    }
    private int getHashCode(Key k){
        return Math.abs(31 * k.hashCode() % this.table.length);
    }
    private void resize(){
        //System.out.println("now resizing");
        LinkedList[] old = this.table;
        this.table = new LinkedList[old.length * 2];
        for(int i=0; i < this.table.length; i++){
            this.table[i] = new LinkedList<Key, Value>();
        }
        this.elementAmount = 0;
        for(int i=0; i < old.length; i++){
            LinkedList currentLinkedlist = old[i];
            LinkedList.Node current = currentLinkedlist.head;
            while(current != null){
                Value v = (Value) current.getValue();
                Key k = (Key) current.getKey();
                current = current.getNext();
                int ArrayElement = getHashCode(k);
                LinkedList resized = this.table[ArrayElement];
                resized.add(k,v);
            }
        }
     }
    private class LinkedList<Key, Value>{
        private Node<Key, Value> head;
        private int size;
        private LinkedList(){
            this.head = null;
            this.size = 0;
        }
        private int size(){
            return size;
        }
        private Value searchValueReturn(Key k) {
            for (Node current = this.head; current != null; current = current.getNext()){
                if (current.getKey().equals(k)){
                    return (Value) current.getValue();
                }
            }
            return null;
        }
        private Node searchNode(Key k) {
            for (Node current = this.head; current != null; current=current.getNext()) {
                if (current.getKey().equals(k)) return current;
            }
            return null;
        }
        private Value remove(Key k){
            Node<Key, Value> current = this.head;
            if(current == null){
                return null;
            }
            if(current.getKey().equals(k)){
                this.head = current.getNext();
                size--;
                return (Value) current.getValue();
            }
            else{
                Node<Key, Value> prev = null;
                while(current.getNext() != null){
                    Node<Key, Value> next = current.getNext();
                    prev = current;
                    if(next != null && next.getKey().equals(k)){
                        prev.setNext(next.getNext());
                        size--;
                        return (Value)next.getValue();
                    }
                    current = current.getNext();
                }
                if(current.getKey().equals(k)){
                    prev.setNext(null);
                    size--;
                    return current.getValue();
                }
                else{
                    return null;
                }
            }
        }
        private Value add(Key k, Value v){
            if(v == null){
                return this.remove(k);
            }
            Node<Key, Value> newNode = new Node<Key, Value>(k, v);
            if(this.head == null){
                this.head = newNode;
                size++;
                return null;
            }
            if(this.searchNode(k) != null){
                return (Value) this.searchNode(k).setValue(v);
            }
            else {
                Node<Key, Value> current = this.head;
                while(current.getNext() != null) {
                    current = current.getNext();
                }
                current.setNext(newNode);
                size++;
                return null;
            }

        }
        private class Node<Key, Value>{
            private Key k;
            private Value v;
            private Node next;
            private Node(Key k, Value v){
                this.k = k;
                this.v = v;
                this.next = null;
            }
            private Node<Key, Value> getNext(){
                return this.next;
            }
            private void setNext(Node n){
                this.next = n;
            }
            private Key getKey(){
                return this.k;
            }
            private Value getValue(){
                return this.v;
            }
            private Value setValue(Value v) {
                Value old = this.v;
                this.v = v;
                return old;

            }
        }

    }
}
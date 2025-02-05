package edu.yu.cs.com1320.project.impl;
import edu.yu.cs.com1320.project.MinHeap;

import java.util.NoSuchElementException;

public class MinHeapImpl<E extends Comparable<E>> extends MinHeap<E>{
    public MinHeapImpl(){
        this.elements = (E[]) new Comparable[1];
    }
    @Override
    public void reHeapify(E element) {
        if(element == null){
            throw new NoSuchElementException();
        }
        int position = this.getArrayIndex(element);
        upHeap(position);
        position = this.getArrayIndex(element);
        downHeap(position);
    }

    @Override
    protected int getArrayIndex(E element) {
        if(element == null){
            throw new NoSuchElementException();
        }
        for(int i = 0; i < this.elements.length; i++){
            if(this.elements[i] == element){
                return i;
            }
        }
        throw new NoSuchElementException();
    }

    @Override
    protected void doubleArraySize() {
         Comparable[] newArray = new Comparable[2*this.elements.length];
         for(int i = 1; i < this.elements.length; i++){
             newArray[i] = this.elements[i];
         }
         this.elements = (E[]) newArray;
    }
}

package edu.yu.cs.com1320.project.impl;
import edu.yu.cs.com1320.project.Stack;
public class StackImpl<T> implements Stack<T>{
    private Object[] array;
    private int lastEntry = -1;
    public StackImpl(){
        this.array = new Object[1];
    }
    @Override
    public void push(T element) {
        if(element == null){
            return;
        }
        if(lastEntry >= this.array.length-1){
            this.resize();
        }
        this.array[lastEntry+1] = element;
        lastEntry++;
    }

    private void resize(){
        int newSize = this.array.length * 2;
        Object[] temp = this.array;
        this.array = new Object[newSize];
        for(int i = 0; i < temp.length; i++){
            this.array[i] = temp[i];
        }
    }

    @Override
    public T pop() {
        if (lastEntry < 0) {
            return null;
        }
        T toReturn = (T)this.array[lastEntry];
        lastEntry--;
        return toReturn;
    }

    @Override
    public T peek() {
        if(lastEntry < 0){
            return null;
        }
        return (T) this.array[lastEntry];
    }

    @Override
    public int size() {
        return lastEntry+1;
    }
}
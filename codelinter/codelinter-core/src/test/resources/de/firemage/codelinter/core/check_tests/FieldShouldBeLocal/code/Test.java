import java.util.List;

public class Test {
    public int a; // Ok, because public
    private int b; // Not ok, because always overwritten
    private Object c; // Not ok, because always overwritten
    private Object d = new Object(); // Ok, because not always overwritten before being read
    
    public static void main(String[] args) {
        Test test = new Test();
        test.foo();
        test.bar();
    }
    
    private void foo() {
        this.a = 1;
        this.b = 1;
        this.c = new Object();
        this.d = new Object();
        if (this.a > 0 && this.b > 0 && this.c != null && this.d != null) {
            // ...
        }
    }

    private void bar() {
        this.a = 1;
        this.b = 1;
        this.c = new Object();
        if (this.a > 0 && this.b > 0 && this.c != null && this.d != null) {
            // ...
        }
        this.d = null;
    }
}
# 面渣逆袭 —— Java基础

> 来源：面渣逆袭Java基础篇V2.1.pdf

1. 什么是 Java？
Java 是⼀⻔⾯向对象的编程语⾔，由 Sun 公司的詹姆斯·⾼斯林团队于 1995 年推出。吸收了 C++ 语⾔中⼤量的优点，但⼜抛弃了 C++ 中容易出错的地⽅，如垃圾回收、指针。同时，Java 又是⼀⻔平台⽆关的编程语⾔，即⼀次编译，处处运⾏。只需要在对应的平台上安装 JDK，就可以实现跨平台，在 Windows、macOS、Linux 操作系统上运⾏。

Java 语⾔和 C 语⾔有哪些区别？
Java 是⼀种跨平台的编程语⾔，通过在不同操作系统上安装对应版本的 JVM 以实现“⼀次编译，处处运⾏”的⽬的。⽽ C 语⾔需要在不同的操作系统上重新编译。Java 实现了内存的⾃动管理，⽽ C 语⾔需要使⽤ malloc 和 free 来⼿动管理内存。

2. Java 语⾔有哪些特点？
Java 语⾔的特点有：①、⾯向对象，主要是封装，继承，多态。②、平台⽆关性，“⼀次编写，到处运⾏”，因此采⽤ Java 语⾔编写的程序具有很好的可移植性。③、⽀持多线程。C++ 语⾔没有内置的多线程机制，因此必须调⽤操作系统的 API 来完成多线程程序设计，⽽ Java 却提供了封装好多线程⽀持；④、⽀持 JIT 编译，也就是即时编译器，它可以在程序运⾏时将字节码转换为热点机器码来提⾼程序的运⾏速度。

3. JVM、JDK 和 JRE 有什么区别？
JVM：也就是 Java 虚拟机，是 Java 实现跨平台的关键所在，不同的操作系统有不同的 JVM 实现。JVM 负责将 Java 字节码转换为特定平台的机器码，并执⾏。JRE：也就是 Java 运⾏时环境，包含了运⾏ Java 程序所必需的库，以及 JVM。JDK：⼀套完整的 Java SDK，包括 JRE，编译器 javac、Java ⽂档⽣成⼯具 javadoc、Java 字节码⼯具 javap 等。为开发者提供了开发、编译、调试 Java 程序的⼀整套环境。简单来说，JDK 包含 JRE，JRE 包含 JVM。

4. 说说什么是跨平台？原理是什么
所谓的跨平台，是指 Java 语⾔编写的程序，⼀次编译后，可以在多个操作系统上运⾏。原理是增加了⼀个中间件 JVM，JVM 负责将 Java 字节码转换为特定平台的机器码，并执⾏。

5. 什么是字节码？采⽤字节码的好处是什么?
所谓的字节码，就是 Java 程序经过编译后产⽣的 .class ⽂件。Java 程序从源代码到运⾏需要经过三步：编译：将源代码⽂件 .java 编译成 JVM 可以识别的字节码⽂件 .class；解释：JVM 执⾏字节码⽂件，将字节码翻译成操作系统能识别的机器码；执⾏：操作系统执⾏⼆进制的机器码。

6. 为什么有⼈说 Java 是“编译与解释并存”的语⾔？
编译型语⾔是指编译器针对特定的操作系统，将源代码⼀次性翻译成可被该平台执⾏的机器码。解释型语⾔是指解释器对源代码进⾏逐⾏解释，解释成特定平台的机器码并执⾏。举个例⼦，我想读⼀本国外的⼩说，我有两种选择：找个翻译，等翻译将⼩说全部都翻译成汉语，⼀次性读完。找个翻译，翻译⼀段我读⼀段，慢慢把书读完。之所以有⼈说 Java 是“编译与解释并存”的语⾔，是因为 Java 程序需要先将 Java 源代码⽂件编译字节码⽂件，再解释执⾏。

7. Java 有哪些数据类型？
Java 的数据类型可以分为两种：基本数据类型和引⽤数据类型。基本数据类型有：①、数值型：整数类型（byte、short、int、long）、浮点类型（float、double）；②、字符型（char）；③、布尔型（boolean）。它们的默认值和占⽤⼤⼩如下所示：
数据类型 默认值 ⼤⼩
boolean false 1 字节或 4 字节
char '\u0000' 2 字节
byte 0 1 字节
short 0 2 字节
int 0 4 字节
long 0L 8 字节
float 0.0f 4 字节
double 0.0 8 字节
引⽤数据类型有：类（class）、接⼝（interface）、数组（[]）。

boolean 类型实际占⽤⼏个字节？
这要依据具体的 JVM 实现细节。Java 虚拟机规范中，并没有明确规定 boolean 类型的⼤⼩，只规定了 boolean 类型的取值 true 或 false。我本机的 64 位 JDK 中，通过 JOL ⼯具查看单独的 boolean 类型，以及 boolean 数组，所占⽤的空间都是 1 个字节。

给Integer最⼤值+1，是什么结果？
当给 Integer.MAX_VALUE 加 1 时，会发⽣溢出，变成 Integer.MIN_VALUE。这是因为 Java 的整数类型采⽤的是⼆进制补码表示法，溢出时值会变成最⼩值。Integer.MAX_VALUE 的⼆进制表示是 01111111 11111111 11111111 11111111（32 位）。加 1 后结果变成 10000000 00000000 00000000 00000000，即 -2147483648（Integer.MIN_VALUE）。

8. ⾃动类型转换、强制类型转换了解吗？
当把⼀个范围较⼩的数值或变量赋给另外⼀个范围较⼤的变量时，会进⾏⾃动类型转换；反之，需要强制转换。这就好像，⼩杯⾥的⽔倒进⼤杯没问题，但⼤杯的⽔倒进⼩杯就可能会溢出。①、float f=3.4 ，对吗？不正确。3.4 默认是双精度，将双精度赋值给浮点型属于下转型（down-casting，也称窄化）会造成精度丢失，因此需要强制类型转换float f =(float)3.4; 或者写成float f =3.4F。②、short s1 = 1; s1 = s1 + 1；对吗？short s1 = 1; s1 += 1; 对吗？short s1 = 1; s1 = s1 + 1; 会编译出错，由于 1 是 int 类型，因此 s1+1 运算结果也是 int 型，需要强制转换类型才能赋值给 short 型。⽽ short s1 = 1; s1 += 1; 可以正确编译，因为 s1+= 1; 相当于 s1 = (short(s1 + 1); 其中有隐含的强制类型转换。

9. 什么是⾃动拆箱/装箱？
装箱：将基本数据类型转换为包装类型，例如 int 转换为 Integer。拆箱：将包装类型转换为基本数据类型。举例：Integer i = 10;  //装箱；int n = i;   //拆箱。再换句话说，i 是 Integer 类型，n 是 int 类型；变量 i 是包装器类，变量 n 是基本数据类型。

10. &和&&有什么区别？
& 是 逻辑与。&& 是短路与运算。逻辑与跟短路与的差别是⾮常⼤的，虽然⼆者都要求运算符左右两端的布尔值都是 true，整个表达式的值才是 true。&& 之所以称为短路运算是因为，如果&& 左边的表达式的值是 false，右边的表达式会直接短路掉，不会进⾏运算。例如在验证⽤户登录时判定⽤户名不是 null ⽽且不是空字符串，应当写为username != null && !username.equals("") ，⼆者的顺序不能交换，更不能⽤ & 运算符，因为第⼀个条件如果不成⽴，根本不能进⾏字符串的 equals ⽐较，会抛出 NullPointerException 异常。注意：逻辑或运算符（| ）和短路或运算符（|| ）的差别也是类似。

11. switch 语句能否⽤在 byte/long/String 类型上？
Java 5 以前 switch(expr) 中，expr 只能是 byte、short、char、int。从 Java 5 开始，Java 中引⼊了枚举类型， expr 也可以是 enum 类型。从 Java 7 开始，expr 还可以是字符串，但是⻓整型在⽬前所有的版本中都是不可以的。

12. break,continue,return 的区别及作⽤？
break 跳出整个循环，不再执⾏循环(结束当前的循环体)；continue 跳出本次循环，继续执⾏下次循环(结束正在执⾏的循环 进⼊下⼀个循环条件)；return 程序返回，不再执⾏下⾯的代码(结束当前的⽅法 直接返回)。

13. ⽤效率最⾼的⽅法计算 2 乘以 8？
2 << 3 。位运算，数字的⼆进制位左移三位相当于乘以 2 的三次⽅。

14. 说说⾃增⾃减运算？
在写代码的过程中，常⻅的⼀种情况是需要某个整数类型变量增加 1 或减少 1，Java 提供了⼀种特殊的运算符，⽤于这种表达式，叫做⾃增运算符（++）和⾃减运算符（--）。++和--运算符可以放在变量之前，也可以放在变量之后。当运算符放在变量之前时(前缀)，先⾃增/减，再赋值；当运算符放在变量之后时(后缀)，先赋值，再⾃增/减。例如，当 b = ++a 时，先⾃增（⾃⼰增加 1），再赋值（赋值给 b）；当 b = a++ 时，先赋值(赋值给 b)，再⾃增（⾃⼰增加 1）。也就是，++a 输出的是 a+1 的值，a++输出的是 a 值。⽤⼀句⼝诀就是：“符号在前就先加/减，符号在后就后加/减”。看⼀下这段代码运⾏结果？答案是 1。有点离谱对不对。对于 JVM ⽽⾔，它对⾃增运算的处理，是会先定义⼀个临时变量来接收 i 的值，然后进⾏⾃增运算，最后⼜将临时
变量赋给了值为 2 的 i，所以最后的结果为 1。
相当于这样的代码：
这段代码会输出什么？
 
答案是 0。
和上⾯的题⽬⼀样的道理，同样是⽤了临时变量，count 实际是等于临时变量的值。
15.float 是怎么表示⼩数的？（补充）
 
2024 年 04 ⽉ 21 ⽇增补
推荐阅读：计算机系统基础（四）浮点数
float 类型的⼩数在计算机中是通过 IEEE 754 标准的单精度浮点数格式来表示的。
 
S：符号位，0 代表正数，1 代表负数；
M：尾数部分，⽤于表示数值的精度；⽐如说 ${1.25 * 2^2}$；1.25 就是尾数；
R：基数，⼗进制中的基数是 10，⼆进制中的基数是 2；
int i  = 1;
i = i++;
System.out.println(i);
int i = 1；
int temp = i;
i++；
i = temp;
System.out.println(i);
int count = 0;
for(int i = 0;i < 100;i++)
{
    count = count++;
}
System.out.println("count = "+count);
int autoAdd(int count)
{
    int temp = count;
    count = count + 1;
    return temp;
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 17 / 89

---

E：指数部分，例如 $10^{-1}$ 中的 -1 就是指数。
这种表示⽅法可以将⾮常⼤或⾮常⼩的数值⽤有限的位数表示出来，但这也意味着可能会有精度上的损失。
单精度浮点数占⽤ 4 字节（32 位），这 32 位被分为三个部分：符号位、指数部分和尾数部分。
1. 符号位（Sign bit）：1 位
2. 指数部分（Exponent）：10 位
3. 尾数部分（Mantissa，或 Fraction）：21 位
按照这个规则，将⼗进制数 25.125 转换为浮点数，转换过程是这样的：
1. 整数部分：25 转换为⼆进制是 11001；
2. ⼩数部分：0.125 转换为⼆进制是 0.001；
3. ⽤⼆进制科学计数法表示：25.125 = $1.001001 \times 2^4$
符号位 S 是 0，表示正数；指数部分 E 是 4，转换为⼆进制是 100；尾数部分 M 是 1.001001。
使⽤浮点数时需要注意，由于精度的限制，进⾏数学运算时可能会遇到舍⼊误差，特别是连续运算累积误差可能会
变得显著。
对于需要⾼精度计算的场景（如⾦融计算），可能需要考虑使⽤BigDecimal 类来避免这种误差。
1. Java ⾯试指南（付费）收录的帆软同学 3 Java 后端⼀⾯的原题：float 是怎么表示⼩数的
16.讲⼀下数据准确性⾼是怎么保证的？（补充）
 
2024 年 04 ⽉ 21 ⽇增补
在⾦融计算中，保证数据准确性有两种⽅案，⼀种使⽤ BigDecimal ，⼀种将浮点数转换为整数 int 进⾏计算。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 18 / 89

---

肯定不能使⽤ float 和 double 类型，它们⽆法避免浮点数运算中常⻅的精度问题，因为这些数据类型采⽤⼆进
制浮点数来表示，⽆法准确地表示，例如 0.1 。
在处理⼩额⽀付或计算时，通过转换为较⼩的货币单位（如分），这样不仅提⾼了运算速度，还保证了计算的准确
性。
1. Java ⾯试指南（付费）收录的字节跳动同学 7 Java 后端实习⼀⾯的原题：讲⼀下数据准确性⾼是怎么
保证的？
⾯向对象
 
17.⾯向对象和⾯向过程的区别?
 
⾯向过程是以过程为核⼼，通过函数完成任务，程序结构是函数+步骤组成的顺序流程。
⾯向对象是以对象为核⼼，通过对象交互完成任务，程序结构是类和对象组成的模块化结构，代码可以通过继承、
组合、多态等⽅式复⽤。
BigDecimal num1 = new BigDecimal("0.1");
BigDecimal num2 = new BigDecimal("0.2");
BigDecimal sum = num1.add(num2);
System.out.println("Sum of 0.1 and 0.2 using BigDecimal: " + sum);  // 输出 0.3，精确计算
int priceInCents = 199;  // 商品价格199分
int quantity = 3;
int totalInCents = priceInCents * quantity;  // 计算总价
System.out.println("Total price in cents: " + totalInCents);  // 输出597分
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 19 / 89

---

在技术派实战项⽬中，像 VO、DTO 都是业务抽象后的对象实体类，⽽ Service、Controller 则是业务逻辑的实
现，这其实就是⾯向对象的思想。
1. Java ⾯试指南（付费）收录的快⼿同学 2 ⼀⾯⾯试原题：⾯向对象和⾯向过程的区别？
2. Java ⾯试指南（付费）收录的字节跳动同学 17 后端技术⾯试原题：⾯向对象 项⽬⾥有哪些⾯向对象的
案例
18.
⾯向对象编程有哪些特性？
 
推荐阅读：深⼊理解 Java 三⼤特性
⾯向对象编程有三⼤特性：封装、继承、多态。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 20 / 89

---

封装是什么？
 
封装是指将数据（属性，或者叫字段）和操作数据的⽅法（⾏为）捆绑在⼀起，形成⼀个独⽴的对象（类的实
例）。
可以看得出，⼥神类对外没有提供 age 的 getter ⽅法，因为⼥神的年龄要保密。
class Nvshen {
    private String name;
    private int age;
    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
    public void setAge(int age) {
        this.age = age;
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 21 / 89

---

所以，封装是把⼀个对象的属性私有化，同时提供⼀些可以被外界访问的⽅法。
继承是什么？
 
继承允许⼀个类（⼦类）继承现有类（⽗类或者基类）的属性和⽅法。以提⾼代码的复⽤性，建⽴类之间的层次关
系。
同时，⼦类还可以重写或者扩展从⽗类继承来的属性和⽅法，从⽽实现多态。
Student 类继承了 Person 类的属性（name、age）和⽅法（eat），同时还有⾃⼰的属性（school）和⽅法
（study）。
什么是多态？
 
多态允许不同类的对象对同⼀消息做出响应，但表现出不同的⾏为（即⽅法的多样性）。
多态其实是⼀种能⼒——同⼀个⾏为具有不同的表现形式；换句话说就是，执⾏⼀段代码，Java 在运⾏时能根据对
象类型的不同产⽣不同的结果。
多态的前置条件有三个：
⼦类继承⽗类
⼦类重写⽗类的⽅法
⽗类引⽤指向⼦类的对象
class Person {
    protected String name;
    protected int age;
    public void eat() {
        System.out.println("吃饭");
    }
}
class Student extends Person {
    private String school;
    public void study() {
        System.out.println("学习");
    }
}
//⼦类继承⽗类
class Wangxiaoer extends Wanger {
    public void write() { // ⼦类重写⽗类⽅法
        System.out.println("记住仇恨，表明我们要奋发图强的⼼智");
    }
    public static void main(String[] args) {
        // ⽗类引⽤指向⼦类对象
        Wanger wanger = new Wangxiaoer();
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 22 / 89

---

为什么Java⾥⾯要多组合少继承？
 
继承适合描述“is-a”的关系，但继承容易导致类之间的强耦合，⼀旦⽗类发⽣改变，⼦类也要随之改变，违背了开
闭原则（尽量不修改现有代码，⽽是添加新的代码来实现）。
组合适合描述“has-a”或“can-do”的关系，通过在类中组合其他类，能够更灵活地扩展功能。组合避免了复杂的类继
承体系，同时遵循了开闭原则和松耦合的设计原则。
举个例⼦，假设我们采⽤继承，每种形状和样式的组合都会导致类的急剧增加：
        wanger.write();
    }
}
class Wanger {
    public void write() {
        System.out.println("沉默王⼆是沙雕");
    }
}
// 基类
class Shape {
    public void draw() {
        System.out.println("Drawing a shape");
    }
}
// 圆形
class Circle extends Shape {
    @Override
    public void draw() {
        System.out.println("Drawing a circle");
    }
}
// 带红⾊的圆形
class RedCircle extends Circle {
    @Override
    public void draw() {
        System.out.println("Drawing a red circle");
    }
}
// 带绿⾊的圆形
class GreenCircle extends Circle {
    @Override
    public void draw() {
        System.out.println("Drawing a green circle");
    }
}
// 类似的，对于矩形也要创建多个类
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 23 / 89

---

组合模式更加灵活，可以将形状和颜⾊分开，松耦合。
形状⼲形状的事情。
class Rectangle extends Shape {
    @Override
    public void draw() {
        System.out.println("Drawing a rectangle");
    }
}
class RedRectangle extends Rectangle {
    @Override
    public void draw() {
        System.out.println("Drawing a red rectangle");
    }
}
// 形状接⼝
interface Shape {
    void draw();
}
// 颜⾊接⼝
interface Color {
    void applyColor();
}
// 圆形的实现
class Circle implements Shape {
    private Color color;  // 通过组合的⽅式持有颜⾊对象
    public Circle(Color color) {
        this.color = color;
    }
    @Override
    public void draw() {
        System.out.print("Drawing a circle with ");
        color.applyColor();  // 调⽤颜⾊的逻辑
    }
}
// 矩形的实现
class Rectangle implements Shape {
    private Color color;
    public Rectangle(Color color) {
        this.color = color;
    }
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 24 / 89

---

颜⾊⼲颜⾊的事情。
1. Java ⾯试指南（付费）收录的国企零碎⾯经同学 9 ⾯试原题：Java ⾯向对象的特性，分别怎么理解的
2. Java ⾯试指南（付费）收录的美团⾯经同学 4 ⼀⾯⾯试原题：Java ⾯向对象的特点
3. Java ⾯试指南（付费）收录的字节跳动同学 20 测开⼀⾯的原题：讲⼀下 JAVA 的特性，什么是多态
4. Java ⾯试指南（付费）收录的京东⾯经同学 7 Java 后端技术⼀⾯⾯试原题：⾯向对象三⼤特性
19.多态解决了什么问题？（补充）
 
2024 年 03 ⽉ 26 ⽇增补
多态指同⼀个接⼝或⽅法在不同的类中有不同的实现，⽐如说动态绑定，⽗类引⽤指向⼦类对象，⽅法的具体调⽤
会延迟到运⾏时决定。
举例，现在有⼀个⽗类 Wanger，⼀个⼦类 Wangxiaoer，都有⼀个 write ⽅法。现在有⼀个⽗类 Wanger 类型的
变量 wanger，它在执⾏ wanger.write() 时，究竟调⽤⽗类 Wanger 的 write() ⽅法，还是⼦类 Wangxiaoer 
的 write() ⽅法呢？
    @Override
    public void draw() {
        System.out.print("Drawing a rectangle with ");
        color.applyColor();
    }
}
// 红⾊的实现
class RedColor implements Color {
    @Override
    public void applyColor() {
        System.out.println("red color");
    }
}
// 绿⾊的实现
class GreenColor implements Color {
    @Override
    public void applyColor() {
        System.out.println("green color");
    }
}
//⼦类继承⽗类
class Wangxiaoer extends Wanger {
    public void write() { // ⼦类覆盖⽗类⽅法
        System.out.println("记住仇恨，表明我们要奋发图强的⼼智");
    }
    public static void main(String[] args) {
        // ⽗类引⽤指向⼦类对象
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 25 / 89

---

答案是在运⾏时根据对象的类型进⾏后期绑定，编译器在编译阶段并不知道对象的类型，但是 Java 的⽅法调⽤机
制能找到正确的⽅法体，然后执⾏，得到正确的结果，这就是多态的作⽤。
多态的实现原理是什么？
 
多态通过动态绑定实现，Java 使⽤虚⽅法表存储⽅法指针，⽅法调⽤时根据对象实际类型从虚⽅法表查找具体实
现。
1. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：多态的⽬的，解决了什么问题？
2. Java ⾯试指南（付费）收录的美团⾯经同学 16 暑期实习⼀⾯⾯试原题：请说说多态、重载和重写
3. Java ⾯试指南（付费）收录的字节跳动⾯经同学19番茄⼩说⼀⾯⾯试原题：多态的⽤法，多态的实现原
理
20.重载和重写的区别？
 
        Wanger[] wangers = { new Wanger(), new Wangxiaoer() };
        for (Wanger wanger : wangers) {
            // 对象是王⼆的时候输出：勿忘国耻
            // 对象是王⼩⼆的时候输出：记住仇恨，表明我们要奋发图强的⼼智
            wanger.write();
        }
    }
}
class Wanger {
    public void write() {
        System.out.println("勿忘国耻");
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 26 / 89

---

推荐阅读：⽅法重写 Override 和⽅法重载 Overload 有什么区别？
如果⼀个类有多个名字相同但参数个数不同的⽅法，我们通常称这些⽅法为⽅法重载（overload）。如果⽅法的功
能是⼀样的，但参数不同，使⽤相同的名字可以提⾼程序的可读性。
如果⼦类具有和⽗类⼀样的⽅法（参数相同、返回类型相同、⽅法名相同，但⽅法体可能不同），我们称之为⽅法
重写（override）。⽅法重写⽤于提供⽗类已经声明的⽅法的特殊实现，是实现多态的基础条件。
⽅法重载发⽣在同⼀个类中，同名的⽅法如果有不同的参数（参数类型不同、参数个数不同或者⼆者都不
同）。
⽅法重写发⽣在⼦类与⽗类之间，要求⼦类与⽗类具有相同的返回类型，⽅法名和参数列表，并且不能⽐⽗
类的⽅法声明更多的异常，遵守⾥⽒代换原则。
什么是⾥⽒代换原则？
 
⾥⽒代换原则也被称为李⽒替换原则（Liskov Substitution Principle, LSP），其规定任何⽗类可以出现的地⽅，
⼦类也⼀定可以出现。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 27 / 89

---

⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 28 / 89

---

LSP 是继承复⽤的基⽯，只有当⼦类可以替换掉⽗类，并且单位功能不受到影响时，⽗类才能真正被复⽤，⽽⼦类
也能够在⽗类的基础上增加新的⾏为。
这意味着⼦类在扩展⽗类时，不应改变⽗类原有的⾏为。例如，如果有⼀个⽅法接受⼀个⽗类对象作为参数，那么
传⼊该⽅法的任何⼦类对象也应该能正常⼯作。
在这个例⼦中，Ostrich（鸵⻦）类违反了 LSP 原则，因为它改变了⽗类 Bird 的⾏为（即⻜⾏）。设计时应该更加
谨慎地使⽤继承关系，确保遵守 LSP 原则。
除了李⽒替换原则外，还有其他⼏个重要的⾯向对象设计原则，它们共同构成了 SOLID 原则，分别是：
①、单⼀职责原则（Single Responsibility Principle, SRP），指⼀个类应该只有⼀个引起它变化的原因，即⼀个类
只负责⼀项职责。这样做的⽬的是使类更加清晰，更容易理解和维护。
②、开闭原则（Open-Closed Principle, OCP），指软件实体应该对扩展开放，对修改关闭。这意味着⼀个类应该
通过扩展来实现新的功能，⽽不是通过修改已有的代码来实现。
举个例⼦，在不遵守开闭原则的情况下，有⼀个需要处理不同形状的绘图功能类。
class Bird {
    void fly() {
        System.out.println("⻦正在⻜");
    }
}
class Duck extends Bird {
    @Override
    void fly() {
        System.out.println("鸭⼦正在⻜");
    }
}
class Ostrich extends Bird {
    // Ostrich违反了LSP，因为鸵⻦不会⻜，但却继承了会⻜的⻦类
    @Override
    void fly() {
        throw new UnsupportedOperationException("鸵⻦不会⻜");
    }
}
class ShapeDrawer {
    public void draw(Shape shape) {
        if (shape instanceof Circle) {
            drawCircle((Circle) shape);
        } else if (shape instanceof Rectangle) {
            drawRectangle((Rectangle) shape);
        }
    }
    
    private void drawCircle(Circle circle) {
        // 画圆形
    }
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 29 / 89

---

每增加⼀种形状，就需要修改⼀次 draw ⽅法，这违反了开闭原则。正确的做法是通过继承和多态来实现新的形状
类，然后在 ShapeDrawer 中添加新的 draw ⽅法。
③、接⼝隔离原则（Interface Segregation Principle, ISP），指客户端不应该依赖它不需要的接⼝。这意味着设计
接⼝时应该尽量精简，不应该设计臃肿庞⼤的接⼝。
④、依赖倒置原则（Dependency Inversion Principle, DIP），指⾼层模块不应该依赖低层模块，⼆者都应该依赖
其抽象；抽象不应该依赖细节，细节应该依赖抽象。这意味着设计时应该尽量依赖接⼝或抽象类，⽽不是实现类。
1. Java ⾯试指南（付费）收录的帆软同学 3 Java 后端⼀⾯的原题：设计⽅法，李⽒原则，还了解哪些设
计原则
2. Java ⾯试指南（付费）收录的美团⾯经同学 16 暑期实习⼀⾯⾯试原题：请说说多态、重载和重写
3. Java ⾯试指南（付费）收录的招银⽹络科技⾯经同学 9 Java 后端技术⼀⾯⾯试原题：Java设计模式中的
开闭原则，⾥⽒替换了解嘛
21.访问修饰符 public、private、protected、以及默认时的区别？
 
    
    private void drawRectangle(Rectangle rectangle) {
        // 画矩形
    }
}
// 抽象的 Shape 类
abstract class Shape {
    public abstract void draw();
}
// 具体的 Circle 类
class Circle extends Shape {
    @Override
    public void draw() {
        // 画圆形
    }
}
// 具体的 Rectangle 类
class Rectangle extends Shape {
    @Override
    public void draw() {
        // 画矩形
    }
}
// 使⽤开闭原则的 ShapeDrawer 类
class ShapeDrawer {
    public void draw(Shape shape) {
        shape.draw();  // 调⽤多态的 draw ⽅法
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 30 / 89

---

Java 中，可以使⽤访问控制符来保护对类、变量、⽅法和构造⽅法的访问。Java ⽀持 4 种不同的访问权限。
default （即默认，什么也不写）: 在同⼀包内可⻅，不使⽤任何修饰符。可以修饰在类、接⼝、变量、⽅
法。
private : 在同⼀类内可⻅。可以修饰变量、⽅法。注意：不能修饰类（外部类）
public : 对所有类可⻅。可以修饰类、接⼝、变量、⽅法
protected : 对同⼀包内的类和所有⼦类可⻅。可以修饰变量、⽅法。注意：不能修饰类（外部类）。
22.this 关键字有什么作⽤？
 
this 是⾃身的⼀个对象，代表对象本身，可以理解为：指向对象本身的⼀个指针。
this 的⽤法在 Java 中⼤体可以分为 3 种：
1. 普通的直接引⽤，this 相当于是指向当前对象本身
2. 形参与成员变量名字重名，⽤ this 来区分：
3. 引⽤本类的构造⽅法
23.
抽象类和接⼝有什么区别？
 
⼀个类只能继承⼀个抽象类；但⼀个类可以实现多个接⼝。所以我们在新建线程类的时候⼀般推荐使⽤实现 
Runnable 接⼝的⽅式，这样线程类还可以继承其他类，⽽不单单是 Thread 类。
抽象类符合 is-a 的关系，⽽接⼝更像是 has-a 的关系，⽐如说⼀个类可以序列化的时候，它只需要实现 
Serializable 接⼝就可以了，不需要去继承⼀个序列化类。
抽象类更多地是⽤来为多个相关的类提供⼀个共同的基础框架，包括状态的初始化，⽽接⼝则是定义⼀套⾏为标
准，让不同的类可以实现同⼀接⼝，提供⾏为的多样化实现。
public Person(String name,int age){
    this.name=name;
    this.age=age;
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 31 / 89

---

抽象类可以定义构造⽅法吗？
 
可以，抽象类可以有构造⽅法。
接⼝可以定义构造⽅法吗？
 
不能，接⼝主要⽤于定义⼀组⽅法规范，没有具体的实现细节。
Java⽀持多继承吗？
 
Java 不⽀持多继承，⼀个类只能继承⼀个类，多继承会引发菱形继承问题。
abstract class Animal {
    protected String name;
    public Animal(String name) {
        this.name = name;
    }
    public abstract void makeSound();
}
public class Dog extends Animal {
    private int age;
    public Dog(String name, int age) {
        super(name);  // 调⽤抽象类的构造函数
        this.age = age;
    }
    @Override
    public void makeSound() {
        System.out.println(name + " says: Bark");
    }
}
class A {
    void show() { System.out.println("A"); }
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 32 / 89

---

接⼝可以多继承吗？
 
接⼝可以多继承，⼀个接⼝可以继承多个接⼝，使⽤逗号分隔。
}
class B extends A {
    void show() { System.out.println("B"); }
}
class C extends A {
    void show() { System.out.println("C"); }
}
// 如果 Java ⽀持多继承
class D extends B, C {
    // 调⽤ show() ⽅法时，D 应该调⽤ B 的 show() 还是 C 的 show()？
}
interface InterfaceA {
    void methodA();
}
interface InterfaceB {
    void methodB();
}
interface InterfaceC extends InterfaceA, InterfaceB {
    void methodC();
}
class MyClass implements InterfaceC {
    public void methodA() {
        System.out.println("Method A");
    }
    public void methodB() {
        System.out.println("Method B");
    }
    public void methodC() {
        System.out.println("Method C");
    }
    public static void main(String[] args) {
        MyClass myClass = new MyClass();
        myClass.methodA();
        myClass.methodB();
        myClass.methodC();
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 33 / 89

---

在上⾯的例⼦中，InterfaceA 和 InterfaceB 是两个独⽴的接⼝。
InterfaceC 继承了 InterfaceA 和 InterfaceB，并且定义了⾃⼰的⽅法 methodC。
MyClass 实现了 InterfaceC，因此需要实现 InterfaceA 和 InterfaceB 中的⽅法 methodA 和 methodB，以及 
InterfaceC 中的⽅法 methodC。
继承和抽象的区别？
 
继承是⼀种允许⼦类继承⽗类属性和⽅法的机制。通过继承，⼦类可以重⽤⽗类的代码。
抽象是⼀种隐藏复杂性和只显示必要部分的技术。在⾯向对象编程中，抽象可以通过抽象类和接⼝实现。
抽象类和普通类的区别？
 
抽象类使⽤ abstract 关键字定义，不能被实例化，只能作为其他类的⽗类。普通类没有 abstract 关键字，可以直
接实例化。
抽象类可以包含抽象⽅法和⾮抽象⽅法。抽象⽅法没有⽅法体，必须由⼦类实现。普通类只能包含⾮抽象⽅法。
1. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：抽象类和接⼝有什么区别？
2. Java ⾯试指南（付费）收录的⽤友⾯试原题：抽象类和接⼝的区别？抽象类可以定义构造⽅法吗？
3. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：继承和抽象的区别
4. Java ⾯试指南（付费）收录的美团同学 2 优选物流调度技术 2 ⾯⾯试原题：抽象类能写构造⽅法吗
（能）接⼝能吗（不能）为什么⼆者有这样的区别
abstract class Animal {
    // 抽象⽅法
    public abstract void makeSound();
    // ⾮抽象⽅法
    public void eat() {
        System.out.println("This animal is eating.");
    }
}
class Dog extends Animal {
    // 实现抽象⽅法
    @Override
    public void makeSound() {
        System.out.println("Woof");
    }
}
public class Test {
    public static void main(String[] args) {
        Dog dog = new Dog();
        dog.makeSound(); // 输出 "Woof"
        dog.eat(); // 输出 "This animal is eating."
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 34 / 89

---

修饰对象
作⽤
变量
静态变量，类级别变量，所有实例共享同⼀份数据。
⽅法
静态⽅法，类级别⽅法，与实例⽆关。
代码块
在类加载时初始化⼀些数据，只执⾏⼀次。
内部类
与外部类绑定但独⽴于外部类实例。
导⼊
可以直接访问静态成员，⽆需通过类名引⽤，简化代码书写，但会降低代码可读性。
5. Java ⾯试指南（付费）收录的去哪⼉同学 1 技术 2 ⾯⾯试原题：接⼝可以多继承吗
6. Java ⾯试指南（付费）收录的京东⾯经同学 7 Java 后端技术⼀⾯⾯试原题：接⼝和抽象类区别
7. Java ⾯试指南（付费）收录的同学 D ⼩⽶⼀⾯原题：java⽀持多继承吗
24.成员变量与局部变量的区别有哪些？
 
1. 从语法形式上看：成员变量是属于类的，⽽局部变量是在⽅法中定义的变量或是⽅法的参数；成员变量可以
被 public , private , static 等修饰符所修饰，⽽局部变量不能被访问控制修饰符及 static 所修饰；但是，成员
变量和局部变量都能被 final 所修饰。
2. 从变量在内存中的存储⽅式来看：如果成员变量是使⽤ static 修饰的，那么这个成员变量是属于类的，如果
没有使⽤ static 修饰，这个成员变量是属于实例的。对象存于堆内存，如果局部变量类型为基本数据类型，
那么存储在栈内存，如果为引⽤数据类型，那存放的是指向堆内存对象的引⽤或者是指向常量池中的地址。
3. 从变量在内存中的⽣存时间上看：成员变量是对象的⼀部分，它随着对象的创建⽽存在，⽽局部变量随着⽅
法的调⽤⽽⾃动消失。
4. 成员变量如果没有被赋初值：则会⾃动以类型的默认值⽽赋值（⼀种情况例外:被 final 修饰的成员变量也必须
显式地赋值），⽽局部变量则不会⾃动赋值。
25.static 关键字了解吗？
 
推荐阅读：详解 Java static 关键字的作⽤
static 关键字可以⽤来修饰变量、⽅法、代码块和内部类，以及导⼊包。
静态变量和实例变量的区别？
 
静态变量: 是被 static 修饰符修饰的变量，也称为类变量，它属于类，不属于类的任何⼀个对象，⼀个类不管创建
多少个对象，静态变量在内存中有且仅有⼀个副本。
实例变量: 必须依存于某⼀实例，需要先创建对象然后通过对象才能访问到它。静态变量可以实现让多个对象共享
内存。
静态⽅法和实例⽅法有何不同?
 
类似地。
静态⽅法：static 修饰的⽅法，也被称为类⽅法。在外部调⽤静态⽅法时，可以使⽤"类名.⽅法名"的⽅式，也可以
使⽤"对象名.⽅法名"的⽅式。静态⽅法⾥不能访问类的⾮静态成员变量和⽅法。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 35 / 89

---

实例⽅法：依存于类的实例，需要使⽤"对象名.⽅法名"的⽅式调⽤；可以访问类的所有成员变量和⽅法。
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学19番茄⼩说⼀⾯⾯试原题：static关键字的使⽤
26.final 关键字有什么作⽤？
 
①、当 final 修饰⼀个类时，表明这个类不能被继承。⽐如，String 类、Integer 类和其他包装类都是⽤ final 修饰
的。
②、当 final 修饰⼀个⽅法时，表明这个⽅法不能被重写（Override）。也就是说，如果⼀个类继承了某个类，并
且想要改变⽗类中被 final 修饰的⽅法的⾏为，是不被允许的。
③、当 final 修饰⼀个变量时，表明这个变量的值⼀旦被初始化就不能被修改。
如果是基本数据类型的变量，其数值⼀旦在初始化之后就不能更改；如果是引⽤类型的变量，在对其初始化之后就
不能再让其指向另⼀个对象。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 36 / 89

---

但是引⽤指向的对象内容可以改变。
1. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：说说 final 关键字
2. Java ⾯试指南（付费）收录的 360 ⾯经同学 3 Java 后端技术⼀⾯⾯试原题：final 的⽤处
3. Java ⾯试指南（付费）收录的京东⾯经同学 8 ⾯试原题：final
27.final、finally、finalize 的区别？
 
①、final 是⼀个修饰符，可以修饰类、⽅法和变量。当 final 修饰⼀个类时，表明这个类不能被继承；当 final 修
饰⼀个⽅法时，表明这个⽅法不能被重写；当 final 修饰⼀个变量时，表明这个变量是个常量，⼀旦赋值后，就不
能再被修改了。
②、finally 是 Java 中异常处理的⼀部分，⽤来创建 try 块后⾯的 finally 块。⽆论 try 块中的代码是否抛出异常，
finally 块中的代码总是会被执⾏。通常，finally 块被⽤来释放资源，如关闭⽂件、数据库连接等。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 37 / 89

---

③、finalize 是Object 类的⼀个⽅法，⽤于在垃圾回收器将对象从内存中清除出去之前做⼀些必要的清理⼯作。
这个⽅法在垃圾回收器准备释放对象占⽤的内存之前被⾃动调⽤。我们不能显式地调⽤ finalize ⽅法，因为它总是
由垃圾回收器在适当的时间⾃动调⽤。
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：final、finally、
finalize 的区别？
28.==和 equals 的区别？
 
在 Java 中，== 操作符和 equals() ⽅法⽤于⽐较两个对象：
①、==：⽤于⽐较两个对象的引⽤，即它们是否指向同⼀个对象实例。
如果两个变量引⽤同⼀个对象实例，== 返回 true ，否则返回 false 。
对于基本数据类型（如 int , double , char 等），== ⽐较的是值是否相等。
②、equals() ⽅法：⽤于⽐较两个对象的内容是否相等。默认情况下，equals() ⽅法的⾏为与 == 相同，即⽐
较对象引⽤，如在超类 Object 中：
然⽽，equals() ⽅法通常被各种类重写。例如，String 类重写了 equals() ⽅法，以便它可以⽐较两个字符
串的字符内容是否完全⼀样。
public boolean equals(Object obj) {
    return (this == obj);
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 38 / 89

---

举个例⼦：
1. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：==和 equals()有什么区别？
2. Java ⾯试指南（付费）收录的 ⼩公司⾯经合集好未来测开⾯经同学 3 测开⼀⾯⾯试原题：==和 equals 
的区别
29.
为什么重写 equals 时必须重写 hashCode ⽅法？
 
因为基于哈希的集合类（如 HashMap）需要基于这⼀点来正确存储和查找对象。
具体地说，HashMap 通过对象的哈希码将其存储在不同的“桶”中，当查找对象时，它需要使⽤ key 的哈希码来确
定对象在哪个桶中，然后再通过 equals() ⽅法找到对应的对象。
String a = new String("沉默王⼆");
String b = new String("沉默王⼆");
// 使⽤ == ⽐较
System.out.println(a == b); // 输出 false，因为 a 和 b 引⽤不同的对象
// 使⽤ equals() ⽐较
System.out.println(a.equals(b)); // 输出 true，因为 a 和 b 的内容相同
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 39 / 89

---

如果重写了 equals() ⽅法⽽没有重写 hashCode() ⽅法，那么被认为相等的对象可能会有不同的哈希码，从⽽导
致⽆法在 HashMap 中正确处理这些对象。
什么是 hashCode ⽅法？
 
hashCode() ⽅法的作⽤是获取哈希码，它会返回⼀个 int 整数，定义在 Object 类中， 是⼀个本地⽅法。
为什么要有 hashCode ⽅法？
 
hashCode ⽅法主要⽤来获取对象的哈希码，哈希码是由对象的内存地址或者对象的属性计算出来的，它是⼀个 
int 类型的整数，通常是不会重复的，因此可以⽤来作为键值对的建，以提⾼查询效率。
例如 HashMap 中的 key 就是通过 hashCode 来实现的，通过调⽤ hashCode ⽅法获取键的哈希码，并将其与右
移 16 位的哈希码进⾏异或运算。
为什么两个对象有相同的 hashcode 值，它们也不⼀定相等？
 
这主要是由于哈希码（hashCode）的本质和⽬的所决定的。
哈希码是通过哈希函数将对象中映射成⼀个整数值，其主要⽬的是在哈希表中快速定位对象的存储位置。
由于哈希函数将⼀个较⼤的输⼊域映射到⼀个较⼩的输出域，不同的输⼊值（即不同的对象）可能会产⽣相同的输
出值（即相同的哈希码）。
这种情况被称为哈希冲突。当两个不相等的对象发⽣哈希冲突时，它们会有相同的 hashCode。
为了解决哈希冲突的问题，哈希表在处理键时，不仅会⽐较键对象的哈希码，还会使⽤ equals ⽅法来检查键对象
是否真正相等。如果两个对象的哈希码相同，但通过 equals ⽅法⽐较结果为 false，那么这两个对象就不被视为相
等。
hashCode 和 equals ⽅法的关系？
 
如果两个对象通过 equals 相等，它们的 hashCode 必须相等。否则会导致哈希表类数据结构（如 HashMap、
HashSet）的⾏为异常。
在哈希表中，如果 equals 相等但 hashCode 不相等，哈希表可能⽆法正确处理这些对象，导致重复元素或键值冲
突等问题。
1. Java ⾯试指南（付费）收录的京东同学 10 后端实习⼀⾯的原题：hashcode 和 equals ⽅法只重写⼀个
⾏不⾏，只重写 equals 没重写 hashcode，map put 的时候会发⽣什么
public native int hashCode();
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
if (p.hash == hash &&
    ((k = p.key) == key || (key != null && key.equals(k))))
    e = p;
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 40 / 89

---

2. Java ⾯试指南（付费）收录的美团同学 2 优选物流调度技术 2 ⾯⾯试原题：为什么重写 equals，建议
必须重写 hashCode ⽅法
3. Java ⾯试指南（付费）收录的美团⾯经同学 3 Java 后端技术⼀⾯⾯试原题：object 有哪些⽅法 
hashcode 和 equals 为什么需要⼀起重写 不重写会导致哪些问题 什么时候会⽤到重写 hashcode 的场
景
4. Java ⾯试指南（付费）收录的京东⾯经同学 7 Java 后端技术⼀⾯⾯试原题：说⼀下hashcode()
5. Java ⾯试指南（付费）收录的京东⾯经同学 8 ⾯试原题：hashcode和equal  
6. Java ⾯试指南（付费）收录的快⼿同学 2 ⼀⾯⾯试原题：HashCode和equals⽅法关系？两个对象的
equals相等hashcode不相等会发⽣什么？
30.Java 是值传递，还是引⽤传递？
 
Java 是值传递，不是引⽤传递。
当⼀个对象被作为参数传递到⽅法中时，参数的值就是该对象的引⽤。引⽤的值是对象在堆中的地址。
对象是存储在堆中的，所以传递对象的时候，可以理解为把变量存储的对象地址给传递过去。
引⽤类型的变量有什么特点？
 
引⽤类型的变量存储的是对象的地址，⽽不是对象本身。因此，引⽤类型的变量在传递时，传递的是对象的地址，
也就是说，传递的是引⽤的值。
1. Java ⾯试指南（付费）收录的华为 OD ⾯经同学 1 ⼀⾯⾯试原题：引⽤类型的变量有什么特点
2. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：JVM 引⽤类型有什么特
点？
31.说说深拷⻉和浅拷⻉的区别?
 
推荐阅读：深⼊理解 Java 浅拷⻉与深拷⻉
在 Java 中，深拷⻉（Deep Copy）和浅拷⻉（Shallow Copy）是两种拷⻉对象的⽅式，它们在拷⻉对象的⽅式上
有很⼤不同。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 41 / 89

---

浅拷⻉会创建⼀个新对象，但这个新对象的属性（字段）和原对象的属性完全相同。如果属性是基本数据类型，拷
⻉的是基本数据类型的值；如果属性是引⽤类型，拷⻉的是引⽤地址，因此新旧对象共享同⼀个引⽤对象。
浅拷⻉的实现⽅式为：实现 Cloneable 接⼝并重写 clone() ⽅法。
class Person implements Cloneable {
    String name;
    int age;
    Address address;
    public Person(String name, int age, Address address) {
        this.name = name;
        this.age = age;
        this.address = address;
    }
    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
class Address {
    String city;
    public Address(String city) {
        this.city = city;
    }
}
public class Main {
    public static void main(String[] args) throws CloneNotSupportedException {
        Address address = new Address("河南省洛阳市");
        Person person1 = new Person("沉默王⼆", 18, address);
        Person person2 = (Person) person1.clone();
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 42 / 89

---

深拷⻉也会创建⼀个新对象，但会递归地复制所有的引⽤对象，确保新对象和原对象完全独⽴。新对象与原对象的
任何更改都不会相互影响。
深拷⻉的实现⽅式有：⼿动复制所有的引⽤对象，或者使⽤序列化与反序列化。
①、⼿动拷⻉
②、序列化与反序列化
        System.out.println(person1.address == person2.address); // true
    }
}
class Person {
    String name;
    int age;
    Address address;
    public Person(String name, int age, Address address) {
        this.name = name;
        this.age = age;
        this.address = address;
    }
    public Person(Person person) {
        this.name = person.name;
        this.age = person.age;
        this.address = new Address(person.address.city);
    }
}
class Address {
    String city;
    public Address(String city) {
        this.city = city;
    }
}
public class Main {
    public static void main(String[] args) {
        Address address = new Address("河南省洛阳市");
        Person person1 = new Person("沉默王⼆", 18, address);
        Person person2 = new Person(person1);
        System.out.println(person1.address == person2.address); // false
    }
}
import java.io.*;
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 43 / 89

---

1. Java ⾯试指南（付费）收录的阿⾥⾯经同学 7 ⾼德地图技术⼀⾯⾯试原题：浅拷⻉和深拷⻉
32.Java 创建对象有哪⼏种⽅式？
 
class Person implements Serializable {
    String name;
    int age;
    Address address;
    public Person(String name, int age, Address address) {
        this.name = name;
        this.age = age;
        this.address = address;
    }
    public Person deepClone() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(this);
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (Person) ois.readObject();
    }
}
class Address implements Serializable {
    String city;
    public Address(String city) {
        this.city = city;
    }
}
public class Main {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Address address = new Address("河南省洛阳市");
        Person person1 = new Person("沉默王⼆", 18, address);
        Person person2 = person1.deepClone();
        System.out.println(person1.address == person2.address); // false
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 44 / 89

---

Java 有四种创建对象的⽅式：
①、new 关键字创建，这是最常⻅和直接的⽅式，通过调⽤类的构造⽅法来创建对象。
②、反射机制创建，反射机制允许在运⾏时创建对象，并且可以访问类的私有成员，在框架和⼯具类中⽐较常⻅。
③、clone 拷⻉创建，通过 clone ⽅法创建对象，需要实现 Cloneable 接⼝并重写 clone ⽅法。
④、序列化机制创建，通过序列化将对象转换为字节流，再通过反序列化从字节流中恢复对象。需要实现 
Serializable 接⼝。
new ⼦类的时候，⼦类和⽗类静态代码块，构造⽅法的执⾏顺序
 
Person person = new Person();
Class clazz = Class.forName("Person");
Person person = (Person) clazz.newInstance();
Person person = new Person();
Person person2 = (Person) person.clone();
Person person = new Person();
ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("person.txt"));
oos.writeObject(person);
ObjectInputStream ois = new ObjectInputStream(new FileInputStream("person.txt"));
Person person2 = (Person) ois.readObject();
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 45 / 89

---

在 Java 中，当创建⼀个⼦类对象时，⼦类和⽗类的静态代码块、构造⽅法的执⾏顺序遵循⼀定的规则。这些规则
主要包括以下⼏个步骤：
1. ⾸先执⾏⽗类的静态代码块（仅在类第⼀次加载时执⾏）。
2. 接着执⾏⼦类的静态代码块（仅在类第⼀次加载时执⾏）。
3. 再执⾏⽗类的构造⽅法。
4. 最后执⾏⼦类的构造⽅法。
下⾯是⼀个详细的代码示例：
执⾏上述代码时，输出结果如下：
静态代码块：在类加载时执⾏，仅执⾏⼀次，按⽗类-⼦类的顺序执⾏。
构造⽅法：在每次创建对象时执⾏，按⽗类-⼦类的顺序执⾏，先初始化块后构造⽅法。
class Parent {
    // ⽗类静态代码块
    static {
        System.out.println("⽗类静态代码块");
    }
    // ⽗类构造⽅法
    public Parent() {
        System.out.println("⽗类构造⽅法");
    }
}
class Child extends Parent {
    // ⼦类静态代码块
    static {
        System.out.println("⼦类静态代码块");
    }
    // ⼦类构造⽅法
    public Child() {
        System.out.println("⼦类构造⽅法");
    }
}
public class Main {
    public static void main(String[] args) {
        new Child();
    }
}
⽗类静态代码块
⼦类静态代码块
⽗类构造⽅法
⼦类构造⽅法
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 46 / 89

---

1. Java ⾯试指南（付费）收录的京东⾯经同学 2 后端⾯试原题：new ⼦类的时候，⼦类和⽗类静态代码
块，构造⽅法的执⾏顺序
String
 
33.String 是 Java 基本数据类型吗？可以被继承吗？
 
不是，String 是⼀个类，属于引⽤数据类型。Java 的基本数据类型包括⼋种：四种整型（byte 、short 、
int 、long ）、两种浮点型（float 、double ）、⼀种字符型（char ）和⼀种布尔型（boolean ）。
String 类可以继承吗？
 
不⾏。String 类使⽤ final 修饰，是所谓的不可变类，⽆法被继承。
String 有哪些常⽤⽅法？
 
我⾃⼰常⽤的有：
1. length() - 返回字符串的⻓度。
2. charAt(int index) - 返回指定位置的字符。
3. substring(int beginIndex, int endIndex) - 返回字符串的⼀个⼦串，从 beginIndex 到 endIndex-
1 。
4. contains(CharSequence s) - 检查字符串是否包含指定的字符序列。
5. equals(Object anotherObject) - ⽐较两个字符串的内容是否相等。
6. indexOf(int ch) 和 indexOf(String str) - 返回指定字符或字符串⾸次出现的位置。
7. replace(char oldChar, char newChar) 和 replace(CharSequence target, CharSequence 
replacement) - 替换字符串中的字符或字符序列。
8. trim() - 去除字符串两端的空⽩字符。
9. split(String regex) - 根据给定正则表达式的匹配拆分此字符串。
1. Java ⾯试指南（付费）收录的 ⼩公司⾯经合集好未来测开⾯经同学 3 测开⼀⾯⾯试原题：String 是 
Java 的基本数据类型吗，String 有哪些⽅法？
34.
String 和 StringBuilder、StringBuffer 的区别？
 
推荐阅读：StringBuffer 和 StringBuilder 两兄弟
String 、StringBuilder 和StringBuffer 在 Java 中都是⽤于处理字符串的，它们之间的区别是，String 是不
可变的，平常开发⽤得最多，当遇到⼤量字符串连接时，就⽤ StringBuilder，它不会⽣成很多新的对象，
StringBuffer 和 StringBuilder 类似，但每个⽅法上都加了 synchronized 关键字，所以是线程安全的。
请说说 String 的特点
 
String 类的对象是不可变的。也就是说，⼀旦⼀个String 对象被创建，它所包含的字符串内容是不可改变
的。
每次对String 对象进⾏修改操作（如拼接、替换等）实际上都会⽣成⼀个新的String 对象，⽽不是修改原
有对象。这可能会导致内存和性能开销，尤其是在⼤量字符串操作的情况下。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 47 / 89

---

请说说 StringBuilder 的特点
 
StringBuilder 提供了⼀系列的⽅法来进⾏字符串的增删改查操作，这些操作都是直接在原有字符串对象的
底层数组上进⾏的，⽽不是⽣成新的 String 对象。
StringBuilder 不是线程安全的。这意味着在没有外部同步的情况下，它不适⽤于多线程环境。
相⽐于String ，在进⾏频繁的字符串修改操作时，StringBuilder 能提供更好的性能。 Java 中的字符串连
+ 操作其实就是通过StringBuilder 实现的。
请说说 StringBuffer 的特点
 
StringBuffer 和StringBuilder 类似，但StringBuffer 是线程安全的，⽅法前⾯都加了synchronized 关键
字。
请总结⼀下使⽤场景
 
String：适⽤于字符串内容不会改变的场景，⽐如说作为 HashMap 的 key。
StringBuilder：适⽤于单线程环境下需要频繁修改字符串内容的场景，⽐如在循环中拼接或修改字符串，是 
String 的完美替代品。
StringBuffer：现在已经不怎么⽤了，因为⼀般不会在多线程场景下去频繁的修改字符串内容。
1. Java ⾯试指南（付费）收录的⽤友⾦融⼀⾯原题：String StringBuffer StringBuilder 有什么区别？
2. Java ⾯试指南（付费）收录的国企⾯试原题：String,StringBuffer,StringBuilder 的区别
3. Java ⾯试指南（付费）收录的美团同学 2 优选物流调度技术 2 ⾯⾯试原题：请说说 String、
StringBuilder、StringBuffer 的区别，为什么这么设计？
4. Java ⾯试指南（付费）收录的字节跳动⾯经同学19番茄⼩说⼀⾯⾯试原题：String，StringBuilder，
StringBuffer的区别，使⽤性能
35.String str1 = new String("abc") 和 String str2 = "abc" 的区别？
 
直接使⽤双引号为字符串变量赋值时，Java ⾸先会检查字符串常量池中是否已经存在相同内容的字符串。
如果存在，Java 就会让新的变量引⽤池中的那个字符串；如果不存在，它会创建⼀个新的字符串，放⼊池中，并让
变量引⽤它。
使⽤ new String("abc") 的⽅式创建字符串时，实际分为两步：
第⼀步，先检查字符串字⾯量 "abc" 是否在字符串常量池中，如果没有则创建⼀个；如果已经存在，则引⽤
它。
第⼆步，在堆中再创建⼀个新的字符串对象，并将其初始化为字符串常量池中 "abc" 的⼀个副本。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 48 / 89

---

也就是说：
String s = new String("abc")创建了⼏个对象？
 
字符串常量池中如果之前已经有⼀个，则不再创建新的，直接引⽤；如果没有，则创建⼀个。
堆中肯定有⼀个，因为只要使⽤了 new 关键字，肯定会在堆中创建⼀个。
1. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：String 变量直接赋值和构造
⽅法赋值==⽐较相等吗？
36.String 是不可变类吗？字符串拼接是如何实现的？
 
1. 推荐阅读：为什么 Java 字符串 String 是不可变的？
2. 推荐阅读：最优雅的 Java 字符串 String 拼接
String 是不可变的，这意味着⼀旦⼀个 String 对象被创建，其存储的⽂本内容就不能被改变。这是因为：
①、不可变性使得 String 对象在使⽤中更加安全。因为字符串经常⽤作参数传递给其他 Java ⽅法，例如⽹络连
接、打开⽂件等。
如果 String 是可变的，这些⽅法调⽤的参数值就可能在不知不觉中被改变，从⽽导致⽹络连接被篡改、⽂件被莫
名其妙地修改等问题。
String s1 = "沉默王⼆";
String s2 = "沉默王⼆";
String s3 = new String("沉默王⼆");
System.out.println(s1 == s2); // 输出 true，因为 s1 和 s2 引⽤的是字符串常量池中同⼀个对象。
System.out.println(s1 == s3); // 输出 false，因为 s3 是通过 new 关键字显式创建的，指向堆上不同的对
象。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 49 / 89

---

②、不可变的对象因为状态不会改变，所以更容易进⾏缓存和重⽤。字符串常量池的出现正是基于这个原因。
当代码中出现相同的字符串字⾯量时，JVM 会确保所有的引⽤都指向常量池中的同⼀个对象，从⽽节约内存。
③、因为 String 的内容不会改变，所以它的哈希值也就固定不变。这使得 String 对象特别适合作为 HashMap 或 
HashSet 等集合的键，因为计算哈希值只需要进⾏⼀次，提⾼了哈希表操作的效率。
字符串拼接是如何实现的？
 
因为 String 是不可变的，因此通过“+”操作符进⾏的字符串拼接，会⽣成新的字符串对象。
例如：
a 和 b 是通过双引号定义的，所以会在字符串常量池中，⽽ ab 是通过“+”操作符拼接的，所以会在堆中⽣成⼀个新
的对象。
Java 8 时，JDK 对“+”号的字符串拼接进⾏了优化，Java 会在编译期基于 StringBuilder 的 append ⽅法进⾏拼接。
下⾯是通过 javap -verbose 命令反编译后的字节码，能清楚的看到 StringBuilder 的创建和 append ⽅法的调
⽤。
String a = "hello ";
String b = "world!";
String ab = a + b;
stack=2, locals=4, args_size=1
     0: ldc           #2                  // String hello
     2: astore_1
     3: ldc           #3                  // String world!
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 50 / 89

---

也就是说，上⾯的代码相当于：
因此，如果笼统地讲，通过加号拼接字符串时会创建多个 String 对象是不准确的。因为加号拼接在编译期还会创
建⼀个 StringBuilder 对象，最终调⽤ toString() ⽅法的时候再返回⼀个新的 String 对象。
那除了使⽤ + 号来拼接字符串，还有 StringBuilder.append() 、String.join() 等⽅式。
推荐阅读：如何拼接字符串？
如何保证 String 不可变？
 
第⼀，String 类内部使⽤⼀个私有的字符数组来存储字符串数据。这个字符数组在创建字符串时被初始化，之后不
允许被改变。
第⼆，String 类没有提供任何可以修改其内容的公共⽅法，像 concat 这些看似修改字符串的操作，实际上都是返
回⼀个新创建的字符串对象，⽽原始字符串对象保持不变。
     5: astore_2
     6: new           #4                  // class java/lang/StringBuilder
     9: dup
    10: invokespecial #5                  // Method java/lang/StringBuilder."<init>":()V
    13: aload_1
    14: invokevirtual #6                  // Method java/lang/StringBuilder.append:
(Ljava/lang/String;)Ljava/lang/StringBuilder;
    17: aload_2
    18: invokevirtual #6                  // Method java/lang/StringBuilder.append:
(Ljava/lang/String;)Ljava/lang/StringBuilder;
    21: invokevirtual #7                  // Method java/lang/StringBuilder.toString:
()Ljava/lang/String;
    24: astore_3
    25: return
String a = "hello ";
String b = "world!";
StringBuilder sb = new StringBuilder();
sb.append(a);
sb.append(b);
String ab = sb.toString();
@Override
public String toString() {
    // Create a copy, don't share the array
    return new String(value, 0, count);
}
private final char value[];
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 51 / 89

---

第三，String 类本身被声明为 final，这意味着它不能被继承。这防⽌了⼦类可能通过添加修改⽅法来改变字符串
内容的可能性。
1. Java ⾯试指南（付费）收录的⼩⽶春招同学 K ⼀⾯⾯试原题：String 是可变的吗，为什么要设计为不可
变
2. Java ⾯试指南（付费）收录的美团同学 2 优选物流调度技术 2 ⾯⾯试原题：String 不可变吗？为什么不
可变？有什么好处？怎么保证不可变。
3. Java ⾯试指南（付费）收录的京东⾯经同学 8 ⾯试原题：字符串拼接 
37.intern ⽅法有什么作⽤？
 
JDK 源码⾥已经对这个⽅法进⾏了说明：
意思也很好懂：
如果当前字符串内容存在于字符串常量池（即 equals()⽅法为 true，也就是内容⼀样），直接返回字符串常
量池中的字符串
否则，将此 String 对象添加到池中，并返回 String 对象的引⽤
Integer
 
38.Integer a= 127，Integer b = 127；Integer c= 128，Integer d = 
128；相等吗?
 
1. 推荐阅读：IntegerCache
2. 推荐阅读：深⼊浅出 Java 拆箱与装箱
public String concat(String str) {
    if (str.isEmpty()) {
        return this;
    }
    int len = value.length;
    int otherLen = str.length();
    char buf[] = Arrays.copyOf(value, len + otherLen);
    str.getChars(buf, len);
    return new String(buf, true);
}
public final class String
* <p>
* When the intern method is invoked, if the pool already contains a
* string equal to this {@code String} object as determined by
* the {@link #equals(Object)} method, then the string from the pool is
* returned. Otherwise, this {@code String} object is added to the
* pool and a reference to this {@code String} object is returned.
* <p>
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 52 / 89

---

a 和 b 相等，c 和 d 不相等。
这个问题涉及到 Java 的⾃动装箱机制以及Integer 类的缓存机制。
对于第⼀对：
a 和b 是相等的。这是因为 Java 在⾃动装箱过程中，会使⽤Integer.valueOf() ⽅法来创建Integer 对象。
Integer.valueOf() ⽅法会针对数值在-128 到 127 之间的Integer 对象使⽤缓存。因此，a 和b 实际上引⽤了
常量池中相同的Integer 对象。
对于第⼆对：
c 和d 不相等。这是因为 128 超出了Integer 缓存的范围(-128 到 127)。
因此，⾃动装箱过程会为c 和d 创建两个不同的Integer 对象，它们有不同的引⽤地址。
可以通过== 运算符来检查它们是否相等：
要⽐较Integer 对象的数值是否相等，应该使⽤equals ⽅法，⽽不是== 运算符：
使⽤equals ⽅法时，c 和d 的⽐较结果为true ，因为equals ⽐较的是对象的数值，⽽不是引⽤地址。
什么是 Integer 缓存？
 
就拿 Integer 的缓存吃来说吧。根据实践发现，⼤部分的数据操作都集中在值⽐较⼩的范围，因此 Integer 搞了个
缓存池，默认范围是 -128 到 127。
Integer a = 127;
Integer b = 127;
Integer c = 128;
Integer d = 128;
System.out.println(a == b); // 输出true
System.out.println(c == d); // 输出false
System.out.println(a.equals(b)); // 输出true
System.out.println(c.equals(d)); // 输出true
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 53 / 89

---

当我们使⽤⾃动装箱来创建这个范围内的 Integer 对象时，Java 会直接从缓存中返回⼀个已存在的对象，⽽不是每
次都创建⼀个新的对象。这意味着，对于这个值范围内的所有 Integer 对象，它们实际上是引⽤相同的对象实例。
Integer 缓存的主要⽬的是优化性能和内存使⽤。对于⼩整数的频繁操作，使⽤缓存可以显著减少对象创建的数
量。
可以在运⾏的时候添加 -Djava.lang.Integer.IntegerCache.high=1000 来调整缓存池的最⼤值。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 54 / 89

---

引⽤是 Integer 类型，= 右侧是 int 基本类型时，会进⾏⾃动装箱，调⽤的其实是 Integer.valueOf() ⽅法，它
会调⽤ IntegerCache。
IntegerCache 是⼀个静态内部类，在静态代码块中会初始化好缓存的值。
new Integer(10) == new Integer(10) 相等吗
 
在 Java 中，使⽤new Integer(10) == new Integer(10) 进⾏⽐较时，结果是 false。
这是因为 new 关键字会在堆（Heap）上为每个 Integer 对象分配新的内存空间，所以这⾥创建了两个不同的 
Integer 对象，它们有不同的内存地址。
public static Integer valueOf(int i) {
    if (i >= IntegerCache.low && i <= IntegerCache.high)
        return IntegerCache.cache[i + (-IntegerCache.low)];
    return new Integer(i);
}
private static class IntegerCache {
    ……
    static {
        //创建Integer对象存储
        for(int k = 0; k < cache.length; k++)
            cache[k] = new Integer(j++);
        ……
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 55 / 89

---

当使⽤==运算符⽐较这两个对象时，实际上⽐较的是它们的内存地址，⽽不是它们的值，因此即使两个对象代表相
同的数值（10），结果也是 false。
1. Java ⾯试指南（付费）收录的⼩⽶春招同学 K ⼀⾯⾯试原题：new Integer(10) == new Integer(10) 相
等吗 常量池
39.String 怎么转成 Integer 的？原理？
 
PS:这道题印象中在⼀些⾯经中出场过⼏次。
String 转成 Integer，主要有两个⽅法：
Integer.parseInt(String s)
Integer.valueOf(String s)
不管哪⼀种，最终还是会调⽤ Integer 类内中的parseInt(String s, int radix) ⽅法。
抛去⼀些边界之类的看看核⼼代码：
去掉枝枝蔓蔓（当然这些枝枝蔓蔓可以去看看，源码 cover 了很多情况），其实剩下的就是⼀个简单的字符串遍历
计算，不过计算⽅式有点反常规，是⽤负的值累减。
public static int parseInt(String s, int radix)
                throws NumberFormatException
    {
        int result = 0;
        //是否是负数
        boolean negative = false;
        //char字符数组下标和⻓度
        int i = 0, len = s.length();
        ……
        int digit;
        //判断字符⻓度是否⼤于0，否则抛出异常
        if (len > 0) {
            ……
            while (i < len) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                //返回指定基数中字符表示的数值。（此处是⼗进制数值）
                digit = Character.digit(s.charAt(i++),radix);
                //进制位乘以数值
                result *= radix;
                result -= digit;
            }
        }
        //根据上⾯得到的是否负数，返回相应的值
        return negative ? result : -result;
    }
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 56 / 89

---

Object
 
40.Object 类的常⻅⽅法？
 
在 Java 中，经常提到⼀个词“万物皆对象”，其中的“万物”指的是 Java 中的所有类，⽽这些类都是 Object 类的⼦
类。
Object 主要提供了 11 个⽅法，⼤致可以分为六类：
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 57 / 89

---

对象⽐较：
 
①、public native int hashCode() ：native ⽅法，⽤于返回对象的哈希码。
按照约定，相等的对象必须具有相等的哈希码。如果重写了 equals ⽅法，就应该重写 hashCode ⽅法。可以使⽤ 
Objects.hash() ⽅法来⽣成哈希码。
②、public boolean equals(Object obj) ：⽤于⽐较 2 个对象的内存地址是否相等。
public native int hashCode();
public int hashCode() {
    return Objects.hash(name, age);
}
public boolean equals(Object obj) {
    return (this == obj);
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 58 / 89

---

如果⽐较的是两个对象的值是否相等，就要重写该⽅法，⽐如 String 类、Integer 类等都重写了该⽅法。举个例
⼦，假如有⼀个 Person 类，我们认为只要年龄和名字相同，就是同⼀个⼈，那么就可以这样重写 equals ⽅法：
对象拷⻉：
 
protected native Object clone() throws CloneNotSupportedException ：naitive ⽅法，返回此对象的⼀
个副本。默认实现只做浅拷⻉，且类必须实现 Cloneable 接⼝。
Object 本身没有实现 Cloneable 接⼝，所以在不重写 clone ⽅法的情况下直接直接调⽤该⽅法会发⽣ 
CloneNotSupportedException 异常。
对象转字符串：
 
public String toString() ：返回对象的字符串表示。默认实现返回类名@哈希码的⼗六进制表示，但通常会
被重写以返回更有意义的信息。
⽐如说⼀个 Person 类，我们可以重写 toString ⽅法，返回⼀个有意义的字符串：
当然了，这项⼯作也可以直接交给 IDE，⽐如 IntelliJ IDEA，直接右键选择 Generate，然后选择 toString ⽅法，就
会⾃动⽣成⼀个 toString ⽅法。
class Person1 {
    private String name;
    private int age;
    // 省略 gettter 和 setter ⽅法
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Person1) {
            Person1 p = (Person1) obj;
            return this.name.equals(p.getName()) && this.age == p.getAge();
        }
        return false;
    }
}
public String toString() {
    return getClass().getName() + "@" + Integer.toHexString(hashCode());
}
public String toString() {
    return "Person{" +
            "name='" + name + '\'' +
            ", age=" + age +
            '}';
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 59 / 89

---

也可以交给 Lombok，使⽤ @Data 注解，它会⾃动⽣成 toString ⽅法。
数组也是⼀个对象，所以通常我们打印数组的时候，会看到诸如 [I@1b6d3586 这样的字符串，这个就是 int 数组
的哈希码。
多线程调度：
 
每个对象都可以调⽤ Object 的 wait/notify ⽅法来实现等待/通知机制。我们来写⼀个例⼦：
解释⼀下：
线程 1 先执⾏，它调⽤了 lock.wait() ⽅法，然后进⼊了等待状态。
线程 2 后执⾏，它调⽤了 lock.notify() ⽅法，然后线程 1 被唤醒了。
①、public final void wait() throws InterruptedException ：调⽤该⽅法会导致当前线程等待，直到另
⼀个线程调⽤此对象的notify() ⽅法或notifyAll() ⽅法。
②、public final native void notify() ：唤醒在此对象监视器上等待的单个线程。如果有多个线程等待，
选择⼀个线程被唤醒。
③、public final native void notifyAll() ：唤醒在此对象监视器上等待的所有线程。
④、public final native void wait(long timeout) throws InterruptedException ：等待 timeout 毫
秒，如果在 timeout 毫秒内没有被唤醒，会⾃动唤醒。
⑥、public final void wait(long timeout, int nanos) throws InterruptedException ：更加精确了，
等待 timeout 毫秒和 nanos 纳秒，如果在 timeout 毫秒和 nanos 纳秒内没有被唤醒，会⾃动唤醒。
反射：
 
public class WaitNotifyDemo {
    public static void main(String[] args) {
        Object lock = new Object();
        new Thread(() -> {
            synchronized (lock) {
                System.out.println("线程1：我要等待");
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("线程1：我被唤醒了");
            }
        }).start();
        new Thread(() -> {
            synchronized (lock) {
                System.out.println("线程2：我要唤醒");
                lock.notify();
                System.out.println("线程2：我已经唤醒了");
            }
        }).start();
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 60 / 89

---

推荐阅读：⼆哥的 Java 进阶之路：掌握 Java 反射
public final native Class<?> getClass() ：⽤于获取对象的类信息，如类名。⽐如说：
输出结果：
垃圾回收：
 
protected void finalize() throws Throwable ：当垃圾回收器决定回收对象占⽤的内存时调⽤此⽅法。⽤于
清理资源，但 Java 不推荐使⽤，因为它不可预测且容易导致问题，Java 9 开始已被弃⽤。
public class GetClassDemo {
    public static void main(String[] args) {
        Person p = new Person();
        Class<? extends Person> aClass = p.getClass();
        System.out.println(aClass.getName());
    }
}
com.itwanger.Person
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 61 / 89

---

1. Java ⾯试指南（付费）收录的⽤友⾦融⼀⾯原题：Object 有哪些常⽤的⽅法？
2. Java ⾯试指南（付费）收录的美团⾯经同学 3 Java 后端技术⼀⾯⾯试原题：object 有哪些⽅法 
hashcode 和 equals 为什么需要⼀起重写 不重写会导致哪些问题 什么时候会⽤到重写 hashcode 的场
景
异常处理
 
41.
Java 中异常处理体系?
 
推荐阅读：⼀⽂彻底搞懂 Java 异常处理
Java 中的异常处理机制⽤于处理程序运⾏过程中可能发⽣的各种异常情况，通常通过 try-catch-finally 语句和 
throw 关键字来实现。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 62 / 89

---

Throwable 是 Java 语⾔中所有错误和异常的基类。它有两个主要的⼦类：Error 和 Exception，这两个类分别代
表了 Java 异常处理体系中的两个分⽀。
Error 类代表那些严重的错误，这类错误通常是程序⽆法处理的。⽐如，OutOfMemoryError 表示内存不⾜，
StackOverflowError 表示栈溢出。这些错误通常与 JVM 的运⾏状态有关，⼀旦发⽣，应⽤程序通常⽆法恢复。
Exception 类代表程序可以处理的异常。它分为两⼤类：编译时异常（Checked Exception）和运⾏时异常
（Runtime Exception）。
①、编译时异常（Checked Exception）：这类异常在编译时必须被显式处理（捕获或声明抛出）。
如果⽅法可能抛出某种编译时异常，但没有捕获它（try-catch）或没有在⽅法声明中⽤ throws ⼦句声明它，那么
编译将不会通过。例如：IOException、SQLException 等。
②、运⾏时异常（Runtime Exception）：这类异常在运⾏时抛出，它们都是 RuntimeException 的⼦类。对于运
⾏时异常，Java 编译器不要求必须处理它们（即不需要捕获也不需要声明抛出）。
运⾏时异常通常是由程序逻辑错误导致的，如 NullPointerException、IndexOutOfBoundsException 等。
1. Java ⾯试指南（付费）收录的京东同学 10 后端实习⼀⾯的原题：Java 编译时异常和运⾏时异常的区别
2. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：异常有哪些分类？
3. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：Error 和 Exception 都
是谁的⼦类？
4. Java ⾯试指南（付费）收录的微众银⾏同学 1 Java 后端⼀⾯的原题：对异常体系了解多少？
5. ⼆哥编程星球球友枕云眠美团 AI ⾯试原题：什么是 java 中的异常处理，checked 异常和 unchecked 
异常有什么区别
6. Java ⾯试指南（付费）收录的京东⾯经同学 8 ⾯试原题：开发中遇到的⼀些异常，异常类型的⽗类，继
承关系等，写程序中如何处理异常？ 
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 63 / 89

---

7. Java ⾯试指南（付费）收录的拼多多⾯经同学 4 技术⼀⾯⾯试原题：error与execption异同，抛出
error程序是⽆法运⾏的吗
42.异常的处理⽅式？
 
①、遇到异常时可以不处理，直接通过throw 和 throws 抛出异常，交给上层调⽤者处理。
throws 关键字⽤于声明可能会抛出的异常，⽽ throw 关键字⽤于抛出异常。
②、使⽤ try-catch 捕获异常，处理异常。
catch和finally的异常可以同时抛出吗？
 
如果 catch 块抛出⼀个异常，⽽ finally 块中也抛出异常，那么最终抛出的将是 finally 块中的异常。catch 块中的
异常会被丢弃，⽽ finally 块中的异常会覆盖并向上传递。
public void test() throws Exception {
    throw new Exception("抛出异常");
}
try {
    //包含可能会出现异常的代码以及声明异常的⽅法
}catch(Exception e) {
    //捕获异常并进⾏处理
}finally {
    //可选，必执⾏的代码
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 64 / 89

---

try 块⾸先抛出⼀个 Exception。
控制流进⼊ catch 块，catch 块中⼜抛出了⼀个 RuntimeException。
但是在 finally 块中，抛出了⼀个 IllegalArgumentException，最终程序抛出的异常是 finally 块中的 
IllegalArgumentException。
虽然 catch 和 finally 中的异常不能同时抛出，但可以⼿动捕获 finally 块中的异常，并将 catch 块中的异常保留下
来，避免被覆盖。常⻅的做法是使⽤⼀个变量临时存储 catch 中的异常，然后在 finally 中处理该异常：
public class Example {
    public static void main(String[] args) {
        try {
            throw new Exception("Exception in try");
        } catch (Exception e) {
            throw new RuntimeException("Exception in catch");
        } finally {
            throw new IllegalArgumentException("Exception in finally");
        }
    }
}
public class Example {
    public static void main(String[] args) {
        Exception catchException = null;
        try {
            throw new Exception("Exception in try");
        } catch (Exception e) {
            catchException = e;
            throw new RuntimeException("Exception in catch");
        } finally {
            try {
                throw new IllegalArgumentException("Exception in finally");
            } catch (IllegalArgumentException e) {
                if (catchException != null) {
                    System.out.println("Catch exception: " + 
catchException.getMessage());
                }
                System.out.println("Finally exception: " + e.getMessage());
            }
        }
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 65 / 89

---

1. Java ⾯试指南（付费）收录的京东⾯经同学 8 ⾯试原题：写程序中如何处理异常？ 
2. Java ⾯试指南（付费）收录的拼多多⾯经同学 4 技术⼀⾯⾯试原题：try-catch-finally抛出异常，catch
和finally的异常可以同时抛出吗？
43.三道经典异常处理代码题
 
题⽬ 1
 
在test() ⽅法中，⾸先有⼀个try 块，接着是⼀个catch 块（⽤于捕获异常），最后是⼀个finally 块（⽆论是
否捕获到异常，finally 块总会执⾏）。
public class TryDemo {
    public static void main(String[] args) {
        System.out.println(test());
    }
    public static int test() {
        try {
            return 1;
        } catch (Exception e) {
            return 2;
        } finally {
            System.out.print("3");
        }
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 66 / 89

---

①、try 块中包含⼀条return 1; 语句。正常情况下，如果try 块中的代码能够顺利执⾏，那么⽅法将返回数字
1 。在这个例⼦中，try 块中没有任何可能抛出异常的操作，因此它会正常执⾏完毕，并准备返回1 。
②、由于try 块中没有异常发⽣，所以catch 块中的代码不会执⾏。
③、⽆论前⾯的代码是否发⽣异常，finally 块总是会执⾏。在这个例⼦中，finally 块包含⼀条
System.out.print("3"); 语句，意味着在⽅法结束前，会在控制台打印出3 。
当执⾏main ⽅法时，控制台的输出将会是：
这是因为finally 块确保了它包含的System.out.print("3"); 会执⾏并打印3 ，随后test() ⽅法返回try 块
中的值1 ，最终结果就是31 。
题⽬ 2
 
执⾏结果：3。
try 返回前先执⾏ finally，结果 finally ⾥不按套路出牌，直接 return 了，⾃然也就⾛不到 try ⾥⾯的 return 了。
注意：finally ⾥⾯使⽤ return 仅存在于⾯试题中，实际开发这么写要挨吊的（
）。
题⽬ 3
 
31
public class TryDemo {
    public static void main(String[] args) {
        System.out.println(test1());
    }
    public static int test1() {
        try {
            return 2;
        } finally {
            return 3;
        }
    }
}
public class TryDemo {
    public static void main(String[] args) {
        System.out.println(test1());
    }
    public static int test1() {
        int i = 0;
        try {
            i = 2;
            return i;
        } finally {
            i = 3;
        }
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 67 / 89

---

执⾏结果：2。
⼤家可能会以为结果应该是 3，因为在 return 前会执⾏ finally，⽽ i 在 finally 中被修改为 3 了，那最终返回 i 不
是应该为 3 吗？
但其实，在执⾏ finally 之前，JVM 会先将 i 的结果暂存起来，然后 finally 执⾏完毕后，会返回之前暂存的结果，
⽽不是返回 i，所以即使 i 已经被修改为 3，最终返回的还是之前暂存起来的结果 2。
1. Java ⾯试指南（付费）收录的京东同学 10 后端实习⼀⾯的原题：return 先执⾏还是 finally 先执⾏
I/O
 
44.Java 中 IO 流分为⼏种?
 
Java IO 流的划分可以根据多个维度进⾏，包括数据流的⽅向（输⼊或输出）、处理的数据单位（字节或字符）、
流的功能以及流是否⽀持随机访问等。
按照数据流⽅向如何划分？
 
输⼊流（Input Stream）：从源（如⽂件、⽹络等）读取数据到程序。
输出流（Output Stream）：将数据从程序写出到⽬的地（如⽂件、⽹络、控制台等）。
按处理数据单位如何划分？
 
字节流（Byte Streams）：以字节为单位读写数据，主要⽤于处理⼆进制数据，如⾳频、图像⽂件等。
字符流（Character Streams）：以字符为单位读写数据，主要⽤于处理⽂本数据。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 68 / 89

---

按功能如何划分？
 
节点流（Node Streams）：直接与数据源或⽬的地相连，如 FileInputStream、FileOutputStream。
处理流（Processing Streams）：对⼀个已存在的流进⾏包装，如缓冲流 BufferedInputStream、
BufferedOutputStream。
管道流（Piped Streams）：⽤于线程之间的数据传输，如 PipedInputStream、PipedOutputStream。
IO 流⽤到了什么设计模式？
 
其实，Java 的 IO 流体系还⽤到了⼀个设计模式——装饰器模式。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 69 / 89

---

Java 缓冲区溢出，如何预防
 
Java 缓冲区溢出主要是由于向缓冲区写⼊的数据超过其能够存储的数据量。可以采⽤这些措施来避免：
①、合理设置缓冲区⼤⼩：在创建缓冲区时，应根据实际需求合理设置缓冲区的⼤⼩，避免创建过⼤或过⼩的缓冲
区。
②、控制写⼊数据量：在向缓冲区写⼊数据时，应该控制写⼊的数据量，确保不会超过缓冲区的容量。Java 的 
ByteBuffer 类提供了remaining() ⽅法，可以获取缓冲区中剩余的可写⼊数据量。
import java.nio.ByteBuffer;
public class ByteBufferExample {
    public static void main(String[] args) {
        // 模拟接收到的数据
        byte[] receivedData = {1, 2, 3, 4, 5};
        int bufferSize = 1024;  // 设置⼀个合理的缓冲区⼤⼩
        // 创建ByteBuffer
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        // 写⼊数据之前检查容量是否⾜够
        if (buffer.remaining() >= receivedData.length) {
            buffer.put(receivedData);
        } else {
            System.out.println("Not enough space in buffer to write data.");
        }
        // 准备读取数据：将limit设置为当前位置，position设回0
        buffer.flip();
        // 读取数据
        while (buffer.hasRemaining()) {
            byte data = buffer.get();
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 70 / 89

---

1. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：Java IO 流 如何划分？
2. Java ⾯试指南（付费）收录的华为⾯经同学 9 Java 通⽤软件开发⼀⾯⾯试原题：Java 缓冲区溢出，如
何预防
45.既然有了字节流,为什么还要有字符流?
 
其实字符流是由 Java 虚拟机将字节转换得到的，问题就出在这个过程还⽐较耗时，并且，如果我们不知道编码类
型就很容易出现乱码问题。
所以， I/O 流就⼲脆提供了⼀个直接操作字符的接⼝，⽅便我们平时对字符进⾏流操作。如果⾳频⽂件、图⽚等媒
体⽂件⽤字节流⽐较好，如果涉及到字符的话使⽤字符流⽐较好。
⽂本存储是字节流还是字符流，视频⽂件呢？
 
在计算机中，⽂本和视频都是按照字节存储的，只是如果是⽂本⽂件的话，我们可以通过字符流的形式去读取，这
样更⽅⾯的我们进⾏直接处理。
⽐如说我们需要在⼀个⼤⽂本⽂件中查找某个字符串，可以直接通过字符流来读取判断。
处理视频⽂件时，通常使⽤字节流（如 Java 中的FileInputStream 、FileOutputStream ）来读取或写⼊数据，
并且会尽量使⽤缓冲流（如BufferedInputStream 、BufferedOutputStream ）来提⾼读写效率。
在技术派实战项⽬项⽬中，对于⽂本，⽐如说⽂章和教程内容，是直接存储在数据库中的，⽽对于视频和图⽚等⼤
⽂件，是存储在 OSS 中的。
因此，⽆论是⽂本⽂件还是视频⽂件，它们在物理存储层⾯都是以字节流的形式存在。区别在于，我们如何通过 
Java 代码来解释和处理这些字节流：作为编码后的字符还是作为⼆进制数据。
1. Java ⾯试指南（付费）收录的国企⾯试原题：⽂本存储是字节流还是字符流，视频⽂件呢？
46.
BIO、NIO、AIO 之间的区别？
 
推荐阅读：Java NIO ⽐传统 IO 强在哪⾥？
Java 常⻅的 IO 模型有三种：BIO、NIO 和 AIO。
            System.out.println("Read data: " + data);
        }
        // 清空缓冲区以便再次使⽤
        buffer.clear();
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 71 / 89

---

BIO：采⽤阻塞式 I/O 模型，线程在执⾏ I/O 操作时被阻塞，⽆法处理其他任务，适⽤于连接数较少的场景。
NIO：采⽤⾮阻塞 I/O 模型，线程在等待 I/O 时可执⾏其他任务，通过 Selector 监控多个 Channel 上的事件，适
⽤于连接数多但连接时间短的场景。
AIO：使⽤异步 I/O 模型，线程发起 I/O 请求后⽴即返回，当 I/O 操作完成时通过回调函数通知线程，适⽤于连接
数多且连接时间⻓的场景。
简单说⼀下 BIO？
 
BIO，也就是传统的 IO，基于字节流或字符流（如 FileInputStream、BufferedReader 等）进⾏⽂件读写，基于 
Socket 和 ServerSocket 进⾏⽹络通信。
对于每个连接，都需要创建⼀个独⽴的线程来处理读写操作。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 72 / 89

---

简单说下 NIO？
 
NIO，JDK 1.4 时引⼊，放在 java.nio 包下，提供了 Channel、Buffer、Selector 等新的抽象，基于 
RandomAccessFile、FileChannel、ByteBuffer 进⾏⽂件读写，基于 SocketChannel 和 ServerSocketChannel 进
⾏⽹络通信。
实际上，“旧”的 I/O 包已经使⽤ NIO 重新实现过，所以在进⾏⽂件读写时，NIO 并⽆法体现出⽐ BIO 更可靠的性
能。
NIO 的魅⼒主要体现在⽹络编程中，服务器可以⽤⼀个线程处理多个客户端连接，通过 Selector 监听多个 
Channel 来实现多路复⽤，极⼤地提⾼了⽹络编程的性能。
缓冲区 Buffer 也能极⼤提升⼀次 IO 操作的效率。
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 73 / 89

---

简单说下 AIO？
 
AIO 是 Java 7 引⼊的，放在 java.nio.channels 包下，提供了 AsynchronousFileChannel、
AsynchronousSocketChannel 等异步 Channel。
它引⼊了异步通道的概念，使得 I/O 操作可以异步进⾏。这意味着线程发起⼀个读写操作后不必等待其完成，可以
⽴即进⾏其他任务，并且当读写操作真正完成时，线程会被异步地通知。
1. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 3 Java 技术⼀⾯⾯试原题：BIO NIO 的区别
2. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：BIO、NIO、AIO 的区别？
3. Java ⾯试指南（付费）收录的 360 ⾯经同学 3 Java 后端技术⼀⾯⾯试原题：说⼀下阻塞⾮阻塞 IO -说
了下 BIO 和 NIO
4. Java ⾯试指南（付费）收录的阿⾥云⾯经同学 22 ⾯经：介绍NIO BIO AIO
序列化
 
47.什么是序列化？什么是反序列化？
 
序列化（Serialization）是指将对象转换为字节流的过程，以便能够将该对象保存到⽂件、数据库，或者进⾏⽹络
传输。
反序列化（Deserialization）就是将字节流转换回对象的过程，以便构建原始对象。
Serializable 接⼝有什么⽤？
 
Serializable 接⼝⽤于标记⼀个类可以被序列化。
AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("test.txt"), 
StandardOpenOption.READ);
ByteBuffer buffer = ByteBuffer.allocate(1024);
Future<Integer> result = fileChannel.read(buffer, 0);
while (!result.isDone()) {
    // do something
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 74 / 89

---

serialVersionUID 有什么⽤？
 
serialVersionUID 是 Java 序列化机制中⽤于标识类版本的唯⼀标识符。它的作⽤是确保在序列化和反序列化过程
中，类的版本是兼容的。
serialVersionUID 被设置为 1L 是⼀种⽐较省事的做法，也可以使⽤ Intellij IDEA 进⾏⾃动⽣成。
但只要 serialVersionUID 在序列化和反序列化过程中保持⼀致，就不会出现问题。
如果不显式声明 serialVersionUID，Java 运⾏时会根据类的详细信息⾃动⽣成⼀个 serialVersionUID。那么当类的
结构发⽣变化时，⾃动⽣成的 serialVersionUID 就会发⽣变化，导致反序列化失败。
Java 序列化不包含静态变量吗？
 
是的，序列化机制只会保存对象的状态，⽽静态变量属于类的状态，不属于对象的状态。
如果有些变量不想序列化，怎么办？
 
可以使⽤transient 关键字修饰不想序列化的变量。
能解释⼀下序列化的过程和作⽤吗？
 
序列化过程通常涉及到以下⼏个步骤：
第⼀步，实现 Serializable 接⼝。
public class Person implements Serializable {
    private String name;
    private int age;
    // 省略 getter 和 setter ⽅法
}
import java.io.Serializable;
public class MyClass implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private int age;
    // getters and setters
}
public class Person implements Serializable {
    private String name;
    private transient int age;
    // 省略 getter 和 setter ⽅法
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 75 / 89

---

第⼆步，使⽤ ObjectOutputStream 来将对象写⼊到输出流中。
第三步，调⽤ ObjectOutputStream 的 writeObject ⽅法，将对象序列化并写⼊到输出流中。
1. Java ⾯试指南（付费）收录的京东⾯经同学 2 后端⾯试原题：⽤过序列化和反序列化吗？
2. ⼆哥编程星球球友枕云眠美团 AI ⾯试原题：你了解 java 的序列化和反序列化吗，能解释⼀下序列化的
过程和作⽤吗
3. Java ⾯试指南（付费）收录的vivo ⾯经同学 10 技术⼀⾯⾯试原题：什么是序列化，什么是反序列化
48.说说有⼏种序列化⽅式？
 
Java 序列化⽅式有很多，常⻅的有三种：
Java 对象序列化 ：Java 原⽣序列化⽅法即通过 Java 原⽣流(InputStream 和 OutputStream 之间的转化)的⽅
式进⾏转化，⼀般是对象输出流 ObjectOutputStream 和对象输⼊流ObjectInputStream 。
Json 序列化：这个可能是我们最常⽤的序列化⽅式，Json 序列化的选择很多，⼀般会使⽤ jackson 包，通过 
ObjectMapper 类来进⾏⼀些操作，⽐如将对象转化为 byte 数组或者将 json 串转化为对象。
ProtoBuff 序列化：ProtocolBuffer 是⼀种轻便⾼效的结构化数据存储格式，ProtoBuff 序列化对象可以很⼤
程度上将其压缩，可以⼤⼤减少数据传输⼤⼩，提⾼系统性能。
⽹络编程
 
49.了解过Socket⽹络套接字吗？（补充）
 
2024 年 11 ⽉ 28 ⽇ 增补
推荐阅读：Java Socket：⻜鸽传书的⽹络套接字
Socket 是⽹络通信的基础，表示两台设备之间通信的⼀个端点。Socket 通常⽤于建⽴ TCP 或 UDP 连接，实现进
程间的⽹络通信。
⼀个简单的 TCP 客户端：
public class Person implements Serializable {
    private String name;
    private int age;
    // 省略构造⽅法、getters和setters
}
ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("person.ser"));
Person person = new Person("沉默王⼆", 18);
out.writeObject(person);
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 76 / 89

---

TCP 服务端：
RPC框架了解吗？
 
RPC是⼀种协议，允许程序调⽤位于远程服务器上的⽅法，就像调⽤本地⽅法⼀样。RPC 通常基于 Socket 通信实
现。
RPC，Remote Procedure Call，远程过程调⽤
RPC 框架⽀持⾼效的序列化（如 Protocol Buffers）和通信协议（如 HTTP/2），屏蔽了底层⽹络通信的细节，开
发者只需关注业务逻辑即可。
常⻅的 RPC 框架包括：
1. gRPC：基于 HTTP/2 和 Protocol Buffers。
class TcpClient {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("127.0.0.1", 8080); // 连接服务器
        BufferedReader in = new BufferedReader(new 
InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println("Hello, Server!"); // 发送消息
        System.out.println("Server response: " + in.readLine()); // 接收服务器响应
        socket.close();
    }
}
class TcpServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080); // 创建服务器端Socket
        System.out.println("Server started, waiting for connection...");
        Socket socket = serverSocket.accept(); // 等待客户端连接
        System.out.println("Client connected: " + socket.getInetAddress());
        BufferedReader in = new BufferedReader(new 
InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        String message;
        while ((message = in.readLine()) != null) {
            System.out.println("Received: " + message);
            out.println("Echo: " + message); // 回送消息
        }
        socket.close();
        serverSocket.close();
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 77 / 89

---

2. Dubbo：阿⾥开源的分布式 RPC 框架，适合微服务场景。
3. Spring Cloud OpenFeign：基于 REST 的轻量级 RPC 框架。
4. Thrift：Apache 的跨语⾔ RPC 框架，⽀持多语⾔代码⽣成。
1. Java ⾯试指南（付费）收录的理想汽⻋⾯经同学 2 ⼀⾯⾯试原题：线程内有哪些通信⽅式？线程之间有
哪些通信⽅式？
泛型
 
50.Java 泛型了解么？
 
推荐阅读：⼿写Java泛型，彻底掌握它
泛型主要⽤于提⾼代码的类型安全，它允许在定义类、接⼝和⽅法时使⽤类型参数，这样可以在编译时检查类型⼀
致性，避免不必要的类型转换和类型错误。
没有泛型的时候，像 List 这样的集合类存储的是 Object 类型，导致从集合中读取数据时，必须进⾏强制类型转
换，否则会引发 ClassCastException。
泛型⼀般有三种使⽤⽅式:泛型类、泛型接⼝、泛型⽅法。
1.泛型类：
如何实例化泛型类：
2.泛型接⼝ ：
List list = new ArrayList();
list.add("hello");
String str = (String) list.get(0);  // 必须强制类型转换
//此处T可以随便写为任意标识，常⻅的如T、E、K、V等形式的参数常⽤于表示泛型
//在实例化泛型类时，必须指定T的具体类型
public class Generic<T>{
    private T key;
    public Generic(T key) {
        this.key = key;
    }
    public T getKey(){
        return key;
    }
}
Generic<Integer> genericInteger = new Generic<Integer>(123456);
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 78 / 89

---

实现泛型接⼝，指定类型：
3.泛型⽅法 ：
使⽤：
泛型常⽤的通配符有哪些？
 
常⽤的通配符为： T，E，K，V，？
？ 表示不确定的 java 类型
T (type) 表示具体的⼀个 java 类型
K V (key value) 分别代表 java 键值中的 Key Value
E (element) 代表 Element
什么是泛型擦除？
 
所谓的泛型擦除，官⽅名叫“类型擦除”。
Java 的泛型是伪泛型，这是因为 Java 在编译期间，所有的类型信息都会被擦掉。
也就是说，在运⾏的时候是没有泛型的。
例如这段代码，往⼀群猫⾥放条狗：
public interface Generator<T> {
    public T method();
}
class GeneratorImpl<T> implements Generator<String>{
    @Override
    public String method() {
        return "hello";
    }
}
   public static < E > void printArray( E[] inputArray )
   {
         for ( E element : inputArray ){
            System.out.printf( "%s ", element );
         }
         System.out.println();
    }
// 创建不同类型数组： Integer, Double 和 Character
Integer[] intArray = { 1, 2, 3 };
String[] stringArray = { "Hello", "World" };
printArray( intArray  );
printArray( stringArray  );
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 79 / 89

---

因为 Java 的范型只存在于源码⾥，编译的时候给你静态地检查⼀下范型类型是否正确，⽽到了运⾏时就不检查
了。上⾯这段代码在 JRE（Java运⾏环境）看来和下⾯这段没区别：
为什么要类型擦除呢？
 
主要是为了向下兼容，因为 JDK5 之前是没有泛型的，为了让 JVM 保持向下兼容，就出了类型擦除这个策略。
1. Java ⾯试指南（付费）收录的 OPPO ⾯经同学 1 ⾯试原题：泛型的作⽤是什么？
注解
 
51.说⼀下你对注解的理解？
 
Java 注解本质上是⼀个标记，可以理解成⽣活中的⼀个⼈的⼀些⼩装扮，⽐如戴什么什么帽⼦，戴什么眼镜。
注解可以标记在类上、⽅法上、属性上等，标记⾃身也可以设置⼀些值，⽐如帽⼦颜⾊是绿⾊。
有了标记之后，我们就可以在编译或者运⾏阶段去识别这些标记，然后搞⼀些事情，这就是注解的⽤处。
例如我们常⻅的 AOP，使⽤注解作为切点就是运⾏期注解的应⽤；⽐如 lombok，就是注解在编译期的运⾏。
注解⽣命周期有三⼤类，分别是：
RetentionPolicy.SOURCE：给编译器⽤的，不会写⼊ class ⽂件
RetentionPolicy.CLASS：会写⼊ class ⽂件，在类加载阶段丢弃，也就是运⾏的时候就没这个信息了
RetentionPolicy.RUNTIME：会写⼊ class ⽂件，永久保存，可以通过反射获取注解信息
所以我上⽂写的是解析的时候，没写具体是解析啥，因为不同的⽣命周期的解析动作是不同的。
像常⻅的：
就是给编译器⽤的，编译器编译的时候检查没问题就 over 了，class ⽂件⾥⾯不会有 Override 这个标记。
再⽐如 Spring 常⻅的 Autowired ，就是 RUNTIME 的，所以在运⾏的时候可以通过反射得到注解的信息，还能拿
到标记的值 required 。
反射
 
52.
什么是反射？应⽤？原理？
 
反射允许 Java 在运⾏时检查和操作类的⽅法和字段。通过反射，可以动态地获取类的字段、⽅法、构造⽅法等信
息，并在运⾏时调⽤⽅法或访问字段。
LinkedList<Cat> cats = new LinkedList<Cat>();
LinkedList list = cats;  // 注意我在这⾥把范型去掉了，但是list和cats是同⼀个链表！
list.add(new Dog());  // 完全没问题！
LinkedList cats = new LinkedList();  // 注意：没有范型！
LinkedList list = cats;
list.add(new Dog());
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 80 / 89

---

⽐如创建⼀个对象是通过 new 关键字来实现的：
Person 类的信息在编译时就确定了，那假如在编译期⽆法确定类的信息，但⼜想在运⾏时获取类的信息、创建类
的实例、调⽤类的⽅法，这时候就要⽤到反射。
反射功能主要通过 java.lang.Class 类及 java.lang.reflect 包中的类如 Method, Field, Constructor 等来实
现。
⽐如说我们可以装来动态加载类并创建对象：
⽐如说我们可以这样来访问字段和⽅法：
反射有哪些应⽤场景？
 
①、Spring 框架就⼤量使⽤了反射来动态加载和管理 Bean。
②、Java 的动态代理（Dynamic Proxy）机制就使⽤了反射来创建代理类。代理类可以在运⾏时动态处理⽅法调
⽤，这在实现 AOP 和拦截器时⾮常有⽤。
Person person = new Person();
String className = "java.util.Date";
Class<?> cls = Class.forName(className);
Object obj = cls.newInstance();
System.out.println(obj.getClass().getName());
// 加载并实例化类
Class<?> cls = Class.forName("java.util.Date");
Object obj = cls.newInstance();
// 获取并调⽤⽅法
Method method = cls.getMethod("getTime");
Object result = method.invoke(obj);
System.out.println("Time: " + result);
// 访问字段
Field field = cls.getDeclaredField("fastTime");
field.setAccessible(true); // 对于私有字段需要这样做
System.out.println("fastTime: " + field.getLong(obj));
Class<?> clazz = Class.forName("com.example.MyClass");
Object instance = clazz.newInstance();
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 81 / 89

---

③、JUnit 和 TestNG 等测试框架使⽤反射机制来发现和执⾏测试⽅法。反射允许框架扫描类，查找带有特定注解
（如 @Test ）的⽅法，并在运⾏时调⽤它们。
反射的原理是什么？
 
Java 程序的执⾏分为编译和运⾏两步，编译之后会⽣成字节码(.class)⽂件，JVM 进⾏类加载的时候，会加载字节
码⽂件，将类型相关的所有信息加载进⽅法区，反射就是去获取这些信息，然后进⾏各种操作。
1. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：Java 反射⽤过吗？
2. Java ⾯试指南（付费）收录的美团⾯经同学 18 成都到家⾯试原题：反射及其应⽤场景
3. Java ⾯试指南（付费）收录的⼩⽶⾯经同学 F ⾯试原题：反射的介绍与使⽤场景
4. Java ⾯试指南（付费）收录的美团⾯经同学 3 Java 后端技术⼀⾯⾯试原题：java 的反射机制，反射的
应⽤场景 AOP 的实现原理是什么，与动态代理和反射有什么区别
5. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 12 Java 技术⾯试原题：java的反射
JDK1.8 新特性
 
JDK 已经出到 17 了，但是你迭代你的版本，我⽤我的 8。JDK1.8 的⼀些新特性，当然现在也不新了，其实在⼯作
中已经很常⽤了。
53.JDK 1.8 都有哪些新特性？
 
JDK 1.8 新增了不少新的特性，如 Lambda 表达式、接⼝默认⽅法、Stream API、⽇期时间 API、Optional 类等。
①、Java 8 允许在接⼝中添加默认⽅法和静态⽅法。
InvocationHandler handler = new MyInvocationHandler();
MyInterface proxyInstance = (MyInterface) Proxy.newProxyInstance(
    MyInterface.class.getClassLoader(),
    new Class<?>[] { MyInterface.class },
    handler
);
Method testMethod = testClass.getMethod("testSomething");
testMethod.invoke(testInstance);
public interface MyInterface {
    default void myDefaultMethod() {
        System.out.println("My default method");
    }
    static void myStaticMethod() {
        System.out.println("My static method");
    }
}
⾯渣逆袭Java基础篇V2-让天下所有的⾯渣都能逆袭
No. 82 / 89

---

②、Lambda 表达式描述了⼀个代码块（或者叫匿名⽅法），可以将其作为参数传递给构造⽅法或者普通⽅法以便
后续执⾏。
《Effective Java》的作者 Josh Bloch 建议使⽤ Lambda 表达式时，最好不要超过 3 ⾏。否则代码可读性会变得很
差。
③、Stream 是对 Java 集合框架的增强，它提供了⼀种⾼效且易于使⽤的数据处理⽅式。
④、Java 8 引⼊了⼀个全新的⽇期和时间 API，位于java.time 包中。这个新的 API 纠正了旧版java.util.Date
类中的许多缺陷。
⑤、引⼊ Optional 是为了减少空指针异常。
1. Java ⾯试指南（付费）收录的招商银⾏⾯经同学 6 招银⽹络科技⾯试原题：JDK1.8 的特性？
2. Java ⾯试指南（付费）收录的联想⾯经同学 7 ⾯试原题：Java 印象⽐较深的版本更新。
54.Lambda 表达式了解多少？
 
Lambda 表达式主要⽤于提供⼀种简洁的⽅式来表示匿名⽅法，使 Java 具备了函数式编程的特性。
public class LamadaTest {
    public static void main(String[] args) {
        new Thread(() -> System.out.println("沉默王⼆")).start();
    }
}
List<String> list = new ArrayList<>();
list.add("中国加油");
list.add("世界加油");
list.add("世界加油");
long count = list.stream().distinct().count();
System.out.println(count);
LocalDate today = LocalDate.now();
System.out.println("Today's Local date : " + today);
LocalTime time = LocalTime.now();
System.out.println("Local time : " + time);
LocalDateTime now = LocalDateTime.now();
System.out.println("Current DateTime : " + now);
Optional<String> optional = Optional.of("沉默王⼆");
optional.isPresent();           // true
optional.get();                 // "沉默王⼆"
optional.orElse("沉默王三");    // "bam"
optional.ifPresent((s) -> System.out.println(s.charAt(0)));     // "沉"

比如说我们可以使用 Lambda 表达式来简化线程的创建：
new Thread(() -> System.out.println("Hello World")).start();
这比以前的匿名内部类要简洁很多。
所谓的函数式编程，就是把函数作为参数传递给方法，或者作为方法的结果返回。比如说我们可以配合 Stream 流进行数据过滤：
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6);
List<Integer> evenNumbers = numbers.stream()
    .filter(n -> n % 2 == 0)
    .collect(Collectors.toList());
其中 n -> n % 2 == 0 就是一个 Lambda 表达式。表示传入一个参数 n，返回 n % 2 == 0 的结果。
Java8 有哪些内置函数式接口？
 
JDK 1.8 API 包含了很多内置的函数式接口。其中就包括我们在老版本中经常见到的 Comparator 和 Runnable，Java 8 为他们都添加了 @FunctionalInterface 注解，以用来支持 Lambda 表达式。
除了这两个之外，还有 Callable、Predicate、Function、Supplier、Consumer 等等。
1. Java 面试指南（付费）收录的招商银行面经同学 6 招银网络科技面试原题：Lamada 表达式的作用？
55.Optional 了解吗？
 
Optional 是用于防范NullPointerException 。
可以将 Optional 看做是包装对象（可能是 null , 也有可能非 null ）的容器。当我们定义了 一个方法，这个方法返回的对象可能是空，也有可能非空的时候，我们就可以考虑用 Optional 来包装它，这也是在 Java 8 被推荐使用的做法。
56.Stream 流用过吗？
 
Stream 流，简单来说，使用 java.util.Stream 对一个包含一个或多个元素的集合做各种操作。这些操作可能是 中间操作 亦或是 终端操作。 终端操作会返回一个结果，而中间操作会返回一个 Stream 流。
Stream 流一般用于集合，我们对一个集合做几个常见操作：
Filter 过滤
Sorted 排序
Map 转换
Match 匹配
List<String> stringCollection = new ArrayList<>();
stringCollection.add("ddd2");
stringCollection.add("aaa2");
stringCollection.add("bbb1");
stringCollection.add("aaa1");
stringCollection.add("bbb3");
stringCollection.add("ccc");
stringCollection.add("bbb2");
stringCollection.add("ddd1");
stringCollection
    .stream()
    .filter((s) -> s.startsWith("a"))
    .forEach(System.out::println);
// "aaa2", "aaa1"
stringCollection
    .stream()
    .sorted()
    .filter((s) -> s.startsWith("a"))
    .forEach(System.out::println);
// "aaa1", "aaa2"
stringCollection
    .stream()
    .map(String::toUpperCase)
    .sorted((a, b) -> b.compareTo(a))
    .forEach(System.out::println);
// "DDD2", "DDD1", "CCC", "BBB3", "BBB2", "AAA2", "AAA1"
// 验证 list 中 string 是否有以 a 开头的, 匹配到第一个，即返回 true
boolean anyStartsWithA =
    stringCollection
        .stream()
        .anyMatch((s) -> s.startsWith("a"));
System.out.println(anyStartsWithA);      // true
// 验证 list 中 string 是否都是以 a 开头的
boolean allStartsWithA =
    stringCollection
        .stream()
        .allMatch((s) -> s.startsWith("a"));
System.out.println(allStartsWithA);      // false
// 验证 list 中 string 是否都不是以 z 开头的,
boolean noneStartsWithZ =
    stringCollection
        .stream()
        .noneMatch((s) -> s.startsWith("z"));
System.out.println(noneStartsWithZ);      // true
Count 计数
count 是一个终端操作，它能够统计 stream 流中的元素总数，返回值是 long 类型。
// 先对 list 中字符串开头为 b 进行过滤，让后统计数量
long startsWithB =
    stringCollection
        .stream()
        .filter((s) -> s.startsWith("b"))
        .count();
System.out.println(startsWithB);    // 3
Reduce
Reduce 中文翻译为：减少、缩小。通过入参的 Function ，我们能够将 list 归约成一个值。它的返回类型是 Optional 类型。
Optional<String> reduced =
    stringCollection
        .stream()
        .sorted()
        .reduce((s1, s2) -> s1 + "#" + s2);
reduced.ifPresent(System.out::println);
// "aaa1#aaa2#bbb1#bbb2#bbb3#ccc#ddd1#ddd2"
以上是常见的几种流式操作，还有其它的一些流式操作，可以帮助我们更便捷地处理集合数据。

---

## 图表说明

> [图] 第1页：这张图没有直接展示技术知识点，而是宣传一本名为《面渣逆袭 Java基础篇》的Java学习资料，内容包含1.3万字、44张手绘图和56道面试题，旨在帮助读者提升Java基础并获得心仪offer。

> [图] 第2页：有技术知识点。图中展示了Java中`hashCode`和`equals`方法的关系，以及Java是值传递还是引用传递的概念，并通过示意图解释了对象在方法调用时的引用传递机制。

> [图] 第3页：有技术知识点。该图展示了Java中多态的概念，通过代码示例说明了父类引用指向子类对象时方法调用的动态绑定机制。

> [图] 第4页：有技术知识点。图中展示了多个与Java技术栈相关的学习资料，包括JVM、Spring、Redis、并发编程等主题的PDF和Markdown文档，涉及面试准备和技术进阶内容。

> [图] 第5页：有技术知识点。该图通过对比浅拷贝和深拷贝在Java中的内存结构，说明了两者在对象复制时对引用类型属性处理方式的不同：浅拷贝共享引用，深拷贝创建独立副本。

> [图] 第6页：有技术知识点。图中展示了多个与前端、后端开发相关的技术主题，如“如何才能达到阿里 P7 水平？”、“前后端分离项目，如何解决跨域问题？”以及“Spring扩展点自定义bean注册扩展机制”等，涵盖了程序员职业发展、跨域处理和Spring框架扩展机制等技术内容。

> [图] 第7页：有技术知识点。该图展示了在本地终端中使用 Maven 构建并运行一个名为 MyDB 的数据库系统，执行了创建表、插入数据和查询数据等 SQL 操作，体现了基本的数据库操作流程和命令行交互过程。

> [图] 第8页：有技术知识点。该图展示了Java语言的四大核心特点：面向对象、平台无关性、支持多线程以及编译与解释并存。

> [图] 第9页：有技术知识点。该图展示了JDK（Java开发工具包）的组成结构，包括JRE（Java运行环境）和开发工具，其中JRE又包含JVM（Java虚拟机）和核心类库。

> [图] 第10页：有技术知识点。该图展示了Java程序从源代码到机器可执行代码的编译和执行过程：.java源代码通过JDK中的javac编译成.class字节码，再由JVM将其转换为机器可执行的二进制机器码。

> [图] 第11页：有技术知识点。该图展示了Java程序的运行流程：Java源代码经过编译生成字节码文件，字节码由不同操作系统的JVM（Java虚拟机）解释执行，从而实现跨平台运行。

> [图] 第12页：有。这张图展示了编程语言中的数据类型分类，包括基本数据类型（如布尔型、数值型、字符型）和引用数据类型（如数组、类、接口）。

> [图] 第14页：有技术知识点。该图展示了Java中基本数据类型与引用数据类型之间的装箱（boxing）和拆箱（unboxing）过程。

> [图] 第14页：有技术知识点。该图展示了Java中基本数据类型的自动类型转换方向，从低精度类型向高精度类型进行自动提升的规则。

> [图] 第16页：有技术知识点。该图展示了Java中`while`循环内`return`、`continue`和`break`语句的控制流程：`return`直接退出方法并返回值，`continue`跳过当前循环剩余代码进入下一次循环，`break`退出循环但继续执行循环后的代码。

> [图] 第18页：有技术知识点。该图展示了IEEE 754单精度浮点数的32位存储结构，分为符号位S、指数E和尾数M三部分。

> [图] 第18页：这张图展示了浮点数在计算机中的二进制表示方法，具体为IEEE 754单精度浮点格式的结构，包括符号位、指数位和尾数位的划分与计算过程。

> [图] 第19页：这张图通过对比《左传》和《史记》的书籍形式，展示了两种不同的历史编纂体例：编年体（面向过程）和纪传体（面向对象），具有历史文献分类的技术知识点。

> [图] 第20页：有技术知识点。该图展示了一个Java项目中的`BaseDTO`类，使用了Lombok的`@Data`注解和Swagger的`@ApiModelProperty`注解来简化代码并提供API文档说明，体现了常见的后端开发实践。

> [图] 第21页：有技术知识点。这张图展示了面向对象编程的三大特性：封装、继承和多态。

> [图] 第26页：有技术知识点。该图展示了Java中面向对象的继承与多态机制，特别是虚方法表（vtable）在方法重写和动态绑定中的作用，以及JVM如何通过`invokevirtual`指令实现运行时方法调用。

> [图] 第27页：有技术知识点。该图展示了Java中的方法重载（Overloading）和方法重写（Overriding）的概念：同一类中同名不同参数的方法为重载，子类覆盖父类同名同参方法为重写。

> [图] 第31页：这张图有技术知识点，它展示了Java中四种访问修饰符（private、default、protected、public）在不同范围内的可见性规则。

> [图] 第32页：有技术知识点。图中展示了在Java接口中定义构造函数是不允许的，IDE提示“Not allowed in interface”，说明接口不能包含构造器，这是Java语言规范的一部分。

> [图] 第36页：有技术知识点。这张图展示了Java中`Integer`类的源代码，包括其常量定义（如`MIN_VALUE`、`MAX_VALUE`）、类声明和静态字符数组`digits`，用于数字到字符串的转换，体现了Java基本数据类型封装类的设计细节。

> [图] 第37页：是的，这张图有技术知识点。它展示了在编程中引用（reference）与对象内容可变性的关系：引用本身不可变（即引用地址不变），但引用指向的内容可以改变。

> [图] 第37页：有技术知识点。图中展示了Java中`final`关键字的使用，说明`final`修饰的变量不能重新赋值，即使其引用的对象内容可以修改。

> [图] 第38页：有技术知识点。图中展示了Java中`Object`类的`finalize()`方法的文档说明，解释了该方法在对象垃圾回收过程中的作用、调用时机以及注意事项，属于Java内存管理和对象生命周期的重要内容。

> [图] 第39页：有技术知识点。图中展示了Java中`String`类的`equals`方法的源码实现，用于比较两个字符串对象是否相等，体现了面向对象编程中的对象比较逻辑。

> [图] 第41页：有技术知识点。该图展示了在编程中引用类型变量（如对象）的赋值与更新过程，说明了引用指向的对象在内存中的变化：当 `user_ref` 被更新为新对象时，它指向了一个新的 `user` 对象，而原对象仍存在于内存中，体现了对象引用和内存管理的基本概念。

> [图] 第42页：有技术知识点。该图通过对比浅拷贝和深拷贝，说明了在对象复制时对嵌套数据结构（如列表）的引用与独立复制的区别。

> [图] 第45页：有技术知识点。该图展示了Java中创建对象的四种主要方式：使用new关键字、通过反射机制、采用clone机制以及通过序列化方式。

> [图] 第49页：是的，这张图有技术知识点。它展示了Java中字符串对象的创建方式及其在内存中的存储机制：`new String("abc")` 在堆中创建新对象，并指向字符串常量池中的 `"abc"`；而直接赋值 `String str2="abc"` 会先在字符串常量池中查找，若存在则直接引用。

> [图] 第50页：有技术知识点。该图展示了JDK 1.8之前字符串拼接的实现方式，说明了字符串变量通过引用指向字符串常量池中的具体值，拼接后的新字符串也会在常量池中创建新的对象。

> [图] 第54页：有技术知识点。该图展示了Java中`Integer`类的内部缓存机制（`IntegerCache`），用于支持自动装箱时的对象身份语义，缓存范围默认为-128到127，可通过系统属性调整上限。

> [图] 第55页：有技术知识点。该图展示了Java程序的运行配置，通过设置`-Djava.lang.Integer.IntegerCache.high=1000`参数来调整Integer缓存的上限，用于测试或优化整数对象的缓存行为。

> [图] 第57页：这张图展示了将数组中的数字按顺序组合成一个负数，再取反得到正整数的过程，体现了字符串拼接与数值转换的技术逻辑。

> [图] 第58页：有技术知识点。该图展示了Java中Object类的主要方法，按功能分类包括对象比较、对象拷贝、对象转字符串、多线程调度、反射和垃圾回收等，涵盖了hashCode()、equals()、clone()、toString()、wait()、notify()、getClass()和finalize()等核心方法。

> [图] 第62页：有技术知识点。这张图展示了Java中`Object`类的`finalize()`方法的源码和相关注释，解释了垃圾回收机制中`finalize`方法的作用、使用注意事项以及为何不推荐使用，属于Java内存管理和对象生命周期的重要概念。

> [图] 第63页：是的，这张图有技术知识点。它展示了Java中异常（Exception）和错误（Error）的继承层次结构，从Throwable类开始，分为Error和Exception两大分支，并列举了常见的子类如RuntimeException、IOException等，有助于理解Java异常处理机制。

> [图] 第64页：有技术知识点。该图展示了Java中异常处理的两个核心机制：抛出异常（使用throw和throws关键字）和捕获异常（使用try-catch结构）。

> [图] 第66页：有技术知识点。该图展示了Java中try-catch-finally语句块的异常处理行为，当finally块中抛出异常时，会覆盖之前catch块中的异常，导致最终抛出finally中的异常。

> [图] 第69页：有技术知识点。这张图展示了Java I/O流的类继承结构，分为字节流和字符流两大类，清晰地呈现了InputStream、OutputStream、Reader和Writer等核心抽象类及其具体实现类的层次关系。

> [图] 第70页：有技术知识点。这张图展示了Java IO流中的装饰器模式（Decorator Pattern）结构，其中`InputStream`是抽象组件类，`ByteArrayInputStream`和`FileInputStream`是具体组件类，`FilterInputStream`是抽象装饰类，而`BufferedInputStream`等是具体装饰类，体现了通过装饰器增强功能的设计思想。

> [图] 第72页：有技术知识点。这张图展示了IO模型的三种分类：BIO（同步阻塞式IO）、NIO（同步非阻塞式IO）和AIO（异步IO），并分别说明了它们的特性及适用场景。

> [图] 第73页：有技术知识点。该图展示了基于NIO（非阻塞I/O）的服务器架构，多个客户端通过Channel（Socket）连接到Selector，由一个线程通过Selector管理多个连接，实现单线程处理多客户端的高效通信模型。

> [图] 第73页：有技术知识点。该图展示了多线程服务器架构，每个客户端通过Socket连接到服务器，服务器为每个客户端创建独立的线程进行处理。

> [图] 第74页：有技术知识点。该图展示了基于NIO（非阻塞I/O）的多客户端服务器架构，通过Selector实现单线程管理多个客户端Channel，提升并发处理效率。

> [图] 第88页：有技术知识点。图中展示了多个与Java技术相关的学习资料，包括JVM、Spring、MyBatis、Redis、并发编程等主题的PDF和Markdown文档，涉及Java开发进阶和面试准备内容。
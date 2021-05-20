package v15.schedulingtest.cases.case1;

/**
 * @author create by TcSnZh on 2021/5/17-上午1:33
 */
class User {
    private String name;
    private int age;
    private double money;

    public User() {
    }

    public User(String name, int age, double money) {
        this.name = name;
        this.age = age;
        this.money = money;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public double getMoney() {
        return money;
    }

    public void setMoney(double money) {
        this.money = money;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", money=" + money +
                '}';
    }
}

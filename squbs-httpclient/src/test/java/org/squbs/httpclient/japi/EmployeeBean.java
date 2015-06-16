package org.squbs.httpclient.japi;

/**
 * Created by lma on 6/15/2015.
 */
public class EmployeeBean {
    private long id;
    private String firstName;
    private String lastName;
    private int age;
    private boolean male;

    public EmployeeBean(long id, String firstName, String lastName, int age, boolean male) {
        this.firstName = firstName;
        this.id = id;
        this.lastName = lastName;
        this.age = age;
        this.male = male;
    }

    public String getFirstName(){return firstName;}

    public String getLastName(){return lastName;}

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EmployeeBean[")
                .append("id=").append(id)
                .append(",firstName=").append(firstName)
                .append(",lastName=").append(firstName)
                .append(",age=").append(firstName)
                .append(",male=").append(firstName)
                .append("]");
        return sb.toString();
    }

    public boolean equals(Object other) {
        if (!(other instanceof EmployeeBean)) return false;
        else {
            EmployeeBean otherEmployee = (EmployeeBean) other;
            return otherEmployee.firstName.equals(firstName)
                    && otherEmployee.id == id
                    && otherEmployee.lastName.equals(lastName)
                    && otherEmployee.age == age
                    && otherEmployee.male == male;
        }
    }
}

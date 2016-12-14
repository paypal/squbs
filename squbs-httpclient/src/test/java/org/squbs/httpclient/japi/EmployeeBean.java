/*
 *  Copyright 2015 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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

    //must have a default one for unmarshalling
    public EmployeeBean(){

    }

    public EmployeeBean(long id1, String firstName1, String lastName1, int age1, boolean male1) {
        this.firstName = firstName1;
        this.id = id1;
        this.lastName = lastName1;
        this.age = age1;
        this.male = male1;
    }

    public long getId(){
        return this.id;
    }

    public int getAge(){
        return this.age;
    }

    public boolean isMale(){
        return this.male;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EmployeeBean[")
                .append("id=").append(id)
                .append(",firstName=").append(firstName)
                .append(",lastName=").append(lastName)
                .append(",age=").append(age)
                .append(",male=").append(male)
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
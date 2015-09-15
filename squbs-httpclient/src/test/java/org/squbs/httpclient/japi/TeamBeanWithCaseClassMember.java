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

import org.squbs.httpclient.dummy.Employee;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lma on 6/15/2015.
 */
public class TeamBeanWithCaseClassMember {

    private String description;
    private List<Employee> members;

    //must have a default one for unmarshalling
    public TeamBeanWithCaseClassMember() {

    }

//    public void setDescription(String desc) {
//        this.description = desc;
//    }
//
//    public void setMembers(List<Employee> mbs) {
//        this.members = mbs;
//    }

    public String getDescription() {
        return this.description;
    }

    public List<Employee> getMembers() {
        return this.members;
    }

    public TeamBeanWithCaseClassMember addMember(Employee employee) {
        List<Employee> all = new ArrayList<Employee>();
        all.addAll(members);
        all.add(employee);
        return new TeamBeanWithCaseClassMember(description, all);
    }

    public TeamBeanWithCaseClassMember(String description1, List<Employee> members1) {
        this.description = description1;
        this.members = members1;
//        nameMap = new HashMap<String, String>();
//        for(int i = 0;i < members.size(); i++){
//            EmployeeBean mem = members.get(i);
//            nameMap.put(mem.getFirstName(),mem.getLastName());
//        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TeamBean[")
                .append("description=").append(description)
                .append(",members=").append(members)
                .append("]");
        return sb.toString();
    }

    public boolean equals(Object other) {
        if (!(other instanceof TeamBeanWithCaseClassMember)) return false;
        else {
            TeamBeanWithCaseClassMember otherTeam = (TeamBeanWithCaseClassMember) other;
            return otherTeam.description.equals(description) && otherTeam.members.equals(members);
        }
    }
}

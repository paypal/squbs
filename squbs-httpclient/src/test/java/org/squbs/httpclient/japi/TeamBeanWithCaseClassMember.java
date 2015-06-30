package org.squbs.httpclient.japi;

import org.squbs.httpclient.dummy.Employee;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lma on 6/15/2015.
 */
public class TeamBeanWithCaseClassMember {

    private String secretField = "mimic";
    private String description;
    private List<Employee> members;
    //private Map<String, String> nameMap;

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

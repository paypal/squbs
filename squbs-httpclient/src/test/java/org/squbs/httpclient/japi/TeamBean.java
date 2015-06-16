package org.squbs.httpclient.japi;

import java.util.ArrayList;

/**
 * Created by lma on 6/15/2015.
 */
public class TeamBean {

    private String description;
    private ArrayList<EmployeeBean> members;
    //private Map<String, String> nameMap;


    public TeamBean(String description, ArrayList<EmployeeBean> members) {
        this.description = description;
        this.members = members;
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
        if (!(other instanceof TeamBean)) return false;
        else {
            TeamBean otherTeam = (TeamBean) other;
            return otherTeam.description.equals(description) && otherTeam.members.equals(members);
        }
    }
}

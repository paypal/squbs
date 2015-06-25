package org.squbs.httpclient.japi;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lma on 6/15/2015.
 */
public class TeamBean {

    private String description;
    private List<EmployeeBean> members;
    //private Map<String, String> nameMap;

    //must have a default one for unmarshalling
    public TeamBean(){

    }

    public TeamBean addMember(EmployeeBean employee){
        List<EmployeeBean> all = new ArrayList<EmployeeBean>();
        all.addAll(members);
        all.add(employee);
        return new TeamBean(description, all);
    }

    public TeamBean(String description1, List<EmployeeBean> members1) {
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
        if (!(other instanceof TeamBean)) return false;
        else {
            TeamBean otherTeam = (TeamBean) other;
            return otherTeam.description.equals(description) && otherTeam.members.equals(members);
        }
    }
}

package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "data_line")
public class SurveyLineEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public float name;

    public int type;

    public int use;

    public String note;

    public long projectId;

    public long createdAt;

    public long updatedAt;
}

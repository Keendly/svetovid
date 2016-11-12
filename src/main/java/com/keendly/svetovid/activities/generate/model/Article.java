package com.keendly.svetovid.activities.generate.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Article {

    public String id;
    public String url;
    public String author;
    public String title;
    public String content;
    public Date date;
    public Map<String, String> actions = new HashMap<>();
}

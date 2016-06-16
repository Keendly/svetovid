package com.keendly.svetovid.model;

import java.util.List;

public class DeliveryItem {

    public String feedId;
    public String title;
    public Boolean withImages;
    public Boolean fullArticle;
    public Boolean markAsRead;

    public List<DeliveryArticle> articles;
}

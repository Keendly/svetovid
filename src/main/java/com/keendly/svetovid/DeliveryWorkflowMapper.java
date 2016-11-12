package com.keendly.svetovid;

import com.keendly.svetovid.activities.extract.model.ExtractRequest;
import com.keendly.svetovid.activities.extract.model.ExtractResult;
import com.keendly.svetovid.activities.generate.model.Article;
import com.keendly.svetovid.activities.generate.model.Book;
import com.keendly.svetovid.activities.generate.model.Section;
import com.keendly.svetovid.activities.generatelinks.model.ActionLink;
import com.keendly.svetovid.activities.generatelinks.model.GenerateLinksArticle;
import com.keendly.svetovid.activities.generatelinks.model.GenerateLinksRequest;
import com.keendly.svetovid.activities.generatelinks.model.GenerateLinksResponse;
import com.keendly.svetovid.activities.send.model.Attachment;
import com.keendly.svetovid.activities.send.model.SendRequest;
import com.keendly.svetovid.activities.update.model.UpdateRequest;
import com.keendly.svetovid.model.DeliveryArticle;
import com.keendly.svetovid.model.DeliveryItem;
import com.keendly.svetovid.model.DeliveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

public class DeliveryWorkflowMapper {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryWorkflowImpl.class);

    public static Optional<ExtractRequest> mapDeliveryRequestToExtractArticlesRequest(DeliveryRequest request){
        ExtractRequest extractRequest = new ExtractRequest();


        boolean articlesFound = false;
        for (DeliveryItem item : request.items){
            if (item.articles == null || item.articles.isEmpty()){
                continue;
            }
            articlesFound = true;
            for (DeliveryArticle article : item.articles){
                ExtractRequest.ExtractRequestItem requestItem = new ExtractRequest.ExtractRequestItem();
                requestItem.url = article.url;
                requestItem.withImages = item.includeImages;
                requestItem.withMetadata = Boolean.FALSE;
                extractRequest.content.add(requestItem);
            }
        }

        if (!articlesFound){
            LOG.warn("No articles found");
            return Optional.empty();
        }
        return Optional.of(extractRequest);
    }

    public static Book mapDeliveryRequestAndExtractResultToBook
        (DeliveryRequest deliveryRequest, List<ExtractResult> articles){
        Book book = new Book();
        book.title = "Keendly Feeds";
        book.creator = "Keendly";
        book.subject = "News";
        book.language = "en-GB";
        for (DeliveryItem item : deliveryRequest.items){
            if (item.articles == null || item.articles.isEmpty()){
                continue;
            }
            Section section = new Section();
            section.title = item.title;

            for (DeliveryArticle article : item.articles){
                Article bookArticle = new Article();
                bookArticle.id = article.id;
                bookArticle.title = article.title;
                bookArticle.author = article.author;
                bookArticle.date  = article.timestamp != null ? new Date(article.timestamp) : null;
                bookArticle.url = article.url;
                if (articles != null && getArticleText(article.url, articles) != null){
                    bookArticle.content = getArticleText(article.url, articles);
                } else {
                    bookArticle.content = article.content;
                }
                section.articles.add(bookArticle);
            }
            book.sections.add(section);
        }
        return book;
    }

    public static Book addActionLinksToBook(Book book, GenerateLinksResponse generateLinksResponse){
        if (generateLinksResponse == null || generateLinksResponse.links == null){
            return book;
        }
        for (Section s : book.sections){
            for (Article a : s.articles){
                if (generateLinksResponse.links.containsKey(a.id)){
                    for (ActionLink link : generateLinksResponse.links.get(a.id)){
                        a.actions.put(link.action, link.link);
                    }
                }
            }
        }
        return book;
    }

    private static String getArticleText(String url, List<ExtractResult> articles){
        for (ExtractResult result : articles){
            if (url.equals(result.url)){
                return result.text;
            }
        }
        return null;
    }

    public static Book mapDeliveryRequestToBook(DeliveryRequest deliveryRequest) {
        Book book = new Book();
        book.title = "Keendly Feeds";
        book.creator = "Keendly";
        book.subject = "News";
        book.language = "en-GB";
        for (DeliveryItem item : deliveryRequest.items){
            if (item.articles == null || item.articles.isEmpty()){
                continue;
            }
            Section section = new Section();
            section.title = item.title;

            for (DeliveryArticle article : item.articles){
                Article bookArticle = new Article();
                bookArticle.title = article.title;
                bookArticle.author = article.author;
                bookArticle.date  = article.timestamp != null ? new Date(article.timestamp) : null;
                bookArticle.url = article.url;
                bookArticle.content = article.content;
                section.articles.add(bookArticle);
            }
            book.sections.add(section);
        }
        return book;
    }

    public static SendRequest mapDeliveryRequestAndGenerateResultToSendRequest
        (DeliveryRequest deliveryRequest, String generateResult){

        SendRequest request = new SendRequest();
        request.message = "Enjoy!";
        request.recipient = deliveryRequest.email;
        request.sender = "kindle@keendly.com";
        request.subject = "Keendly Delivery";
        Attachment attachment = new Attachment();
        attachment.bucket = "keendly";
        attachment.key = generateResult;
        request.attachment = attachment;
        request.dryRun = deliveryRequest.dryRun;

        return request;
    }

    public static UpdateRequest mapDeliveryRequestAndSendResultToUpdateRequest
        (DeliveryRequest deliveryRequest){

        UpdateRequest request = new UpdateRequest();
        request.deliveryId = deliveryRequest.id;
        request.userId = deliveryRequest.userId;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        request.date = sdf.format(new Date());
//        request.date = sendResult.date;
//        request.status = sendResult.status.name();
//        request.error = sendResult.errorDescription;
        request.dryRun = deliveryRequest.dryRun;

        return request;
    }

    public static GenerateLinksRequest mapDeliveryRequestToGenerateLinksRequest(DeliveryRequest deliveryRequest){
        Map<String, List<GenerateLinksArticle>> articles = new HashMap<>();
        for (DeliveryItem deliveryItem : deliveryRequest.items){
            for (DeliveryArticle deliveryArticle : deliveryItem.articles){
                List<GenerateLinksArticle> links = new ArrayList<>();
                GenerateLinksArticle a = new GenerateLinksArticle();
                a.userId = deliveryRequest.userId;
                a.operation = deliveryItem.markAsRead ? "keep_unread" : "mark_as_read";
                a.title = deliveryArticle.title;
                links.add(a);
                articles.put(deliveryArticle.id, links);
            }
        }
        GenerateLinksRequest request = new GenerateLinksRequest();
        request.articles = articles;
        return request;
    }
}

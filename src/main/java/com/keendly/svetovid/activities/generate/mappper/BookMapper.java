package com.keendly.svetovid.activities.generate.mappper;

public class BookMapper {

//    public static Book toBook(DeliveryRequest deliveryRequest, List<ExtractResult> articles){
//        Book book = new Book();
//        book.setTitle("Keendly Feeds");
//        book.setCreator("Keendly");
//        book.setSubject("News");
//        book.setLanguage("en-GB");
//        book.setSections(new ArrayList<>());
//        for (DeliveryItem item : deliveryRequest.getItems()){
//            Section section = new Section();
//            section.setTitle(item.getTitle());
//            section.setArticles(new ArrayList<>());
//            for (DeliveryArticle article : item.getArticles()){
//                Article bookArticle = new Article();
//                bookArticle.setTitle(article.getTitle());
//                bookArticle.setAuthor(article.getAuthor());
//                bookArticle.setDate(new Date(article.getTimestamp()));
//                bookArticle.setUrl(article.getUrl());
//                if (articles != null && getArticleText(article.getUrl(), articles) != null){
//                    bookArticle.setContent(getArticleText(article.getUrl(), articles));
//                } else {
//                    bookArticle.setContent(article.getContent());
//                }
//                section.getArticles().add(bookArticle);
//            }
//            book.getSections().add(section);
//        }
//        return book;
//    }
//
//    private static String getArticleText(String url, List<ExtractResult> articles){
//        for (ExtractResult result : articles){
//            if (url.equals(result.getUrl())){
//                return result.getText();
//            }
//        }
//        return null;
//    }

}

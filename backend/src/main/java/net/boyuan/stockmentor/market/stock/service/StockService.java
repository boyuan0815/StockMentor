package net.boyuan.stockmentor.market.stock.service;

public interface StockService {

//    Java not support default parameters
    void fetchAndSave(String symbols);
    void fetchAndSave(String symbols, int outputSize);
}
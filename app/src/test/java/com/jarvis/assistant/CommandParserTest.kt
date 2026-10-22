package com.jarvis.assistant

import com.jarvis.assistant.nlp.CommandParser
import com.jarvis.assistant.nlp.JarvisIntent
import com.jarvis.assistant.nlp.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun testBengaliCallRelation() {
        val intent = CommandParser.parse("amar bou ke call koro")
        assertTrue(intent is JarvisIntent.CallTarget)
        val call = intent as JarvisIntent.CallTarget
        assertEquals("bou", call.target)
        assertTrue(call.isRelationHint)
        assertEquals(SupportedLanguage.BENGALI, call.language)
    }

    @Test
    fun testBengaliTrainRelation() {
        val intent = CommandParser.parse("Jarvis, amar bou er nam Priya")
        assertTrue(intent is JarvisIntent.TrainRelation)
        val train = intent as JarvisIntent.TrainRelation
        assertEquals("bou", train.relation)
        assertEquals("Priya", train.contactName)
        assertEquals(SupportedLanguage.BENGALI, train.language)
    }

    @Test
    fun testBengaliTrainHoloRelation() {
        val intent = CommandParser.parse("amar bou holo Priya")
        assertTrue(intent is JarvisIntent.TrainRelation)
        val train = intent as JarvisIntent.TrainRelation
        assertEquals("bou", train.relation)
        assertEquals("Priya", train.contactName)
        assertEquals(SupportedLanguage.BENGALI, train.language)
    }

    @Test
    fun testHindiCallRelation() {
        val intent = CommandParser.parse("mummy ko call lagao")
        assertTrue(intent is JarvisIntent.CallTarget)
        val call = intent as JarvisIntent.CallTarget
        assertEquals("mummy", call.target)
        assertTrue(call.isRelationHint)
        assertEquals(SupportedLanguage.HINDI, call.language)
    }

    @Test
    fun testHindiTrainRelation() {
        val intent = CommandParser.parse("meri mummy ka naam Sunita hai")
        assertTrue(intent is JarvisIntent.TrainRelation)
        val train = intent as JarvisIntent.TrainRelation
        assertEquals("mummy", train.relation)
        assertEquals("Sunita", train.contactName)
        assertEquals(SupportedLanguage.HINDI, train.language)
    }

    @Test
    fun testEnglishCallRelation() {
        val intent = CommandParser.parse("call my wife")
        assertTrue(intent is JarvisIntent.CallTarget)
        val call = intent as JarvisIntent.CallTarget
        assertEquals("wife", call.target)
        assertTrue(call.isRelationHint)
        assertEquals(SupportedLanguage.ENGLISH, call.language)
    }

    @Test
    fun testEnglishTrainRelation() {
        val intent = CommandParser.parse("remember my boss is Rahul")
        assertTrue(intent is JarvisIntent.TrainRelation)
        val train = intent as JarvisIntent.TrainRelation
        assertEquals("boss", train.relation)
        assertEquals("Rahul", train.contactName)
        assertEquals(SupportedLanguage.ENGLISH, train.language)
    }

    @Test
    fun testEnglishDirectCall() {
        val intent = CommandParser.parse("call Priya Sharma")
        assertTrue(intent is JarvisIntent.CallTarget)
        val call = intent as JarvisIntent.CallTarget
        assertEquals("Priya Sharma", call.target)
        assertEquals(SupportedLanguage.ENGLISH, call.language)
    }
}

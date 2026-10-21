package io.github.ernisn.applingo.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device: pinyin comes from the platform's ICU transliterator. */
@RunWith(AndroidJUnit4::class)
class PinyinTest {

    @Test
    fun chineseLabelsGetFullPinyinAndInitials() {
        assertEquals(listOf("weixin", "wx"), Pinyin.spellings("微信"))
        assertEquals(listOf("zhifubao", "zfb"), Pinyin.spellings("支付宝"))
    }

    @Test
    fun latinPartsAreKept() {
        assertTrue(Pinyin.spellings("QQ音乐").containsAll(listOf("qqyinyue", "qqyy")))
    }

    @Test
    fun polyphonicCharactersGetTheirOtherReadings() {
        // ICU reads 行 as "xing"; in bank names it is "hang".
        assertTrue(Pinyin.spellings("招商银行").containsAll(listOf("zhaoshangyinhang", "zsyh")))
        assertTrue(Pinyin.spellings("重庆").contains("chongqing"))
    }

    @Test
    fun labelsWithoutHanHaveNoPinyin() {
        assertEquals(emptyList<String>(), Pinyin.spellings("Google Maps"))
    }
}

package com.zhizhu.zhicode.compose.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** CC Switch 供应商图标资源规格。 */
data class ProviderBrandIconSpec(
    @DrawableRes val resId: Int,
    val tintable: Boolean = false,
    val tile: Boolean = false,
    val needsLightBacking: Boolean = false,
)

/** CC Switch 提取的全部供应商图标映射。 */
object ProviderBrandIcons {
    private val specs: Map<String, ProviderBrandIconSpec> = mapOf(
        "88api" to ProviderBrandIconSpec(R.drawable.ccs_88api, tintable = false, tile = true, needsLightBacking = false),
        "9527code" to ProviderBrandIconSpec(R.drawable.ccs_9527code, tintable = true, tile = false, needsLightBacking = false),
        "a6api" to ProviderBrandIconSpec(R.drawable.ccs_a6api, tintable = false, tile = false, needsLightBacking = true),
        "aicodemirror" to ProviderBrandIconSpec(R.drawable.ccs_aicodemirror, tintable = false, tile = false, needsLightBacking = false),
        "aicodewith" to ProviderBrandIconSpec(R.drawable.ccs_aicodewith, tintable = true, tile = false, needsLightBacking = false),
        "aicoding" to ProviderBrandIconSpec(R.drawable.ccs_aicoding, tintable = false, tile = false, needsLightBacking = true),
        "aicoming" to ProviderBrandIconSpec(R.drawable.ccs_aicoming, tintable = false, tile = true, needsLightBacking = false),
        "aigocode" to ProviderBrandIconSpec(R.drawable.ccs_aigocode, tintable = false, tile = false, needsLightBacking = false),
        "aihubmix" to ProviderBrandIconSpec(R.drawable.ccs_aihubmix, tintable = false, tile = true, needsLightBacking = false),
        "alltokens" to ProviderBrandIconSpec(R.drawable.ccs_alltokens, tintable = false, tile = true, needsLightBacking = false),
        "alltokens-si" to ProviderBrandIconSpec(R.drawable.ccs_alltokens_si, tintable = false, tile = false, needsLightBacking = true),
        "amux" to ProviderBrandIconSpec(R.drawable.ccs_amux, tintable = true, tile = false, needsLightBacking = false),
        "anthropic" to ProviderBrandIconSpec(R.drawable.ccs_anthropic, tintable = true, tile = false, needsLightBacking = false),
        "apikeyfun" to ProviderBrandIconSpec(R.drawable.ccs_apikeyfun, tintable = false, tile = true, needsLightBacking = false),
        "apinebula" to ProviderBrandIconSpec(R.drawable.ccs_apinebula, tintable = false, tile = true, needsLightBacking = false),
        "atlascloud" to ProviderBrandIconSpec(R.drawable.ccs_atlascloud, tintable = false, tile = false, needsLightBacking = true),
        "aws" to ProviderBrandIconSpec(R.drawable.ccs_aws, tintable = false, tile = false, needsLightBacking = true),
        "azure" to ProviderBrandIconSpec(R.drawable.ccs_azure, tintable = false, tile = false, needsLightBacking = false),
        "bailing" to ProviderBrandIconSpec(R.drawable.ccs_bailing, tintable = false, tile = false, needsLightBacking = true),
        "baseten" to ProviderBrandIconSpec(R.drawable.ccs_baseten, tintable = true, tile = false, needsLightBacking = false),
        "botcf" to ProviderBrandIconSpec(R.drawable.ccs_botcf, tintable = false, tile = false, needsLightBacking = false),
        "buytoken" to ProviderBrandIconSpec(R.drawable.ccs_buytoken, tintable = false, tile = true, needsLightBacking = false),
        "byteplus" to ProviderBrandIconSpec(R.drawable.ccs_byteplus, tintable = false, tile = false, needsLightBacking = true),
        "ccsub" to ProviderBrandIconSpec(R.drawable.ccs_ccsub, tintable = false, tile = true, needsLightBacking = false),
        "cherryin" to ProviderBrandIconSpec(R.drawable.ccs_cherryin, tintable = false, tile = true, needsLightBacking = false),
        "claude" to ProviderBrandIconSpec(R.drawable.ccs_claude, tintable = false, tile = false, needsLightBacking = false),
        "claudecn" to ProviderBrandIconSpec(R.drawable.ccs_claudecn, tintable = false, tile = true, needsLightBacking = false),
        "cline" to ProviderBrandIconSpec(R.drawable.ccs_cline, tintable = true, tile = false, needsLightBacking = false),
        "code0" to ProviderBrandIconSpec(R.drawable.ccs_code0, tintable = false, tile = false, needsLightBacking = true),
        "crazyrouter" to ProviderBrandIconSpec(R.drawable.ccs_crazyrouter, tintable = true, tile = false, needsLightBacking = false),
        "cubence" to ProviderBrandIconSpec(R.drawable.ccs_cubence, tintable = false, tile = false, needsLightBacking = true),
        "deepinfra" to ProviderBrandIconSpec(R.drawable.ccs_deepinfra, tintable = true, tile = false, needsLightBacking = false),
        "deepseek" to ProviderBrandIconSpec(R.drawable.ccs_deepseek, tintable = false, tile = false, needsLightBacking = false),
        "digitalocean" to ProviderBrandIconSpec(R.drawable.ccs_digitalocean, tintable = true, tile = false, needsLightBacking = false),
        "dmxapi" to ProviderBrandIconSpec(R.drawable.ccs_dmxapi, tintable = false, tile = false, needsLightBacking = true),
        "doubao" to ProviderBrandIconSpec(R.drawable.ccs_doubao, tintable = false, tile = false, needsLightBacking = false),
        "eflowcode" to ProviderBrandIconSpec(R.drawable.ccs_eflowcode, tintable = false, tile = true, needsLightBacking = false),
        "fenno" to ProviderBrandIconSpec(R.drawable.ccs_fenno, tintable = false, tile = false, needsLightBacking = true),
        "fluxa" to ProviderBrandIconSpec(R.drawable.ccs_fluxa, tintable = false, tile = true, needsLightBacking = false),
        "gemini" to ProviderBrandIconSpec(R.drawable.ccs_gemini, tintable = false, tile = false, needsLightBacking = false),
        "genesis" to ProviderBrandIconSpec(R.drawable.ccs_genesis, tintable = false, tile = false, needsLightBacking = false),
        "google" to ProviderBrandIconSpec(R.drawable.ccs_google, tintable = false, tile = false, needsLightBacking = false),
        "grooroute" to ProviderBrandIconSpec(R.drawable.ccs_grooroute, tintable = false, tile = true, needsLightBacking = false),
        "groq" to ProviderBrandIconSpec(R.drawable.ccs_groq, tintable = true, tile = false, needsLightBacking = false),
        "haoai" to ProviderBrandIconSpec(R.drawable.ccs_haoai, tintable = false, tile = true, needsLightBacking = false),
        "huggingface" to ProviderBrandIconSpec(R.drawable.ccs_huggingface, tintable = false, tile = false, needsLightBacking = true),
        "huoshan" to ProviderBrandIconSpec(R.drawable.ccs_huoshan, tintable = false, tile = false, needsLightBacking = true),
        "ikuncode" to ProviderBrandIconSpec(R.drawable.ccs_ikuncode, tintable = false, tile = true, needsLightBacking = false),
        "kimi" to ProviderBrandIconSpec(R.drawable.ccs_kimi, tintable = false, tile = false, needsLightBacking = true),
        "lmstudio" to ProviderBrandIconSpec(R.drawable.ccs_lmstudio, tintable = true, tile = false, needsLightBacking = false),
        "longcat" to ProviderBrandIconSpec(R.drawable.ccs_longcat, tintable = false, tile = false, needsLightBacking = true),
        "meta" to ProviderBrandIconSpec(R.drawable.ccs_meta, tintable = false, tile = false, needsLightBacking = false),
        "micu" to ProviderBrandIconSpec(R.drawable.ccs_micu, tintable = false, tile = false, needsLightBacking = false),
        "minimax" to ProviderBrandIconSpec(R.drawable.ccs_minimax, tintable = false, tile = false, needsLightBacking = false),
        "mistral" to ProviderBrandIconSpec(R.drawable.ccs_mistral, tintable = false, tile = false, needsLightBacking = false),
        "moark" to ProviderBrandIconSpec(R.drawable.ccs_moark, tintable = true, tile = false, needsLightBacking = false),
        "modelflare" to ProviderBrandIconSpec(R.drawable.ccs_modelflare, tintable = false, tile = true, needsLightBacking = false),
        "modelscope" to ProviderBrandIconSpec(R.drawable.ccs_modelscope, tintable = false, tile = false, needsLightBacking = false),
        "nebius" to ProviderBrandIconSpec(R.drawable.ccs_nebius, tintable = true, tile = false, needsLightBacking = false),
        "nekocode" to ProviderBrandIconSpec(R.drawable.ccs_nekocode, tintable = false, tile = false, needsLightBacking = true),
        "novita" to ProviderBrandIconSpec(R.drawable.ccs_novita, tintable = false, tile = false, needsLightBacking = false),
        "nvidia" to ProviderBrandIconSpec(R.drawable.ccs_nvidia, tintable = false, tile = false, needsLightBacking = false),
        "ollama" to ProviderBrandIconSpec(R.drawable.ccs_ollama, tintable = true, tile = false, needsLightBacking = false),
        "openai" to ProviderBrandIconSpec(R.drawable.ccs_openai, tintable = true, tile = false, needsLightBacking = false),
        "opencode" to ProviderBrandIconSpec(R.drawable.ccs_opencode, tintable = false, tile = false, needsLightBacking = true),
        "openmodel" to ProviderBrandIconSpec(R.drawable.ccs_openmodel, tintable = false, tile = true, needsLightBacking = false),
        "openrouter" to ProviderBrandIconSpec(R.drawable.ccs_openrouter, tintable = true, tile = false, needsLightBacking = false),
        "ospreyai" to ProviderBrandIconSpec(R.drawable.ccs_ospreyai, tintable = false, tile = false, needsLightBacking = true),
        "packycode" to ProviderBrandIconSpec(R.drawable.ccs_packycode, tintable = true, tile = false, needsLightBacking = false),
        "pipellm" to ProviderBrandIconSpec(R.drawable.ccs_pipellm, tintable = false, tile = true, needsLightBacking = false),
        "ppio" to ProviderBrandIconSpec(R.drawable.ccs_ppio, tintable = false, tile = false, needsLightBacking = false),
        "qianwenai" to ProviderBrandIconSpec(R.drawable.ccs_qianwenai, tintable = false, tile = false, needsLightBacking = false),
        "qiniu" to ProviderBrandIconSpec(R.drawable.ccs_qiniu, tintable = false, tile = true, needsLightBacking = false),
        "qwencloud" to ProviderBrandIconSpec(R.drawable.ccs_qwencloud, tintable = false, tile = false, needsLightBacking = false),
        "rc" to ProviderBrandIconSpec(R.drawable.ccs_rc, tintable = false, tile = false, needsLightBacking = false),
        "runapi" to ProviderBrandIconSpec(R.drawable.ccs_runapi, tintable = false, tile = true, needsLightBacking = false),
        "runtoken" to ProviderBrandIconSpec(R.drawable.ccs_runtoken, tintable = false, tile = true, needsLightBacking = false),
        "sambanova" to ProviderBrandIconSpec(R.drawable.ccs_sambanova, tintable = false, tile = false, needsLightBacking = false),
        "shengsuanyun" to ProviderBrandIconSpec(R.drawable.ccs_shengsuanyun, tintable = false, tile = true, needsLightBacking = false),
        "soleapi" to ProviderBrandIconSpec(R.drawable.ccs_soleapi, tintable = true, tile = false, needsLightBacking = false),
        "soruxgpt" to ProviderBrandIconSpec(R.drawable.ccs_soruxgpt, tintable = false, tile = true, needsLightBacking = false),
        "soshow" to ProviderBrandIconSpec(R.drawable.ccs_soshow, tintable = false, tile = false, needsLightBacking = true),
        "sssaicode" to ProviderBrandIconSpec(R.drawable.ccs_sssaicode, tintable = false, tile = true, needsLightBacking = false),
        "stepfun" to ProviderBrandIconSpec(R.drawable.ccs_stepfun, tintable = false, tile = false, needsLightBacking = false),
        "streamlake" to ProviderBrandIconSpec(R.drawable.ccs_streamlake, tintable = false, tile = false, needsLightBacking = false),
        "stringai" to ProviderBrandIconSpec(R.drawable.ccs_stringai, tintable = false, tile = true, needsLightBacking = false),
        "subrouter" to ProviderBrandIconSpec(R.drawable.ccs_subrouter, tintable = false, tile = true, needsLightBacking = false),
        "sudocode" to ProviderBrandIconSpec(R.drawable.ccs_sudocode, tintable = false, tile = true, needsLightBacking = false),
        "sudocode-us" to ProviderBrandIconSpec(R.drawable.ccs_sudocode_us, tintable = false, tile = false, needsLightBacking = true),
        "teamorouter" to ProviderBrandIconSpec(R.drawable.ccs_teamorouter, tintable = false, tile = true, needsLightBacking = false),
        "tencent" to ProviderBrandIconSpec(R.drawable.ccs_tencent, tintable = false, tile = false, needsLightBacking = false),
        "therouter" to ProviderBrandIconSpec(R.drawable.ccs_therouter, tintable = true, tile = false, needsLightBacking = false),
        "tokenfactory" to ProviderBrandIconSpec(R.drawable.ccs_tokenfactory, tintable = false, tile = true, needsLightBacking = false),
        "tokenflux" to ProviderBrandIconSpec(R.drawable.ccs_tokenflux, tintable = false, tile = true, needsLightBacking = false),
        "tokenplus" to ProviderBrandIconSpec(R.drawable.ccs_tokenplus, tintable = false, tile = true, needsLightBacking = false),
        "tuzi" to ProviderBrandIconSpec(R.drawable.ccs_tuzi, tintable = false, tile = true, needsLightBacking = false),
        "vapi" to ProviderBrandIconSpec(R.drawable.ccs_vapi, tintable = false, tile = true, needsLightBacking = false),
        "vercel" to ProviderBrandIconSpec(R.drawable.ccs_vercel, tintable = true, tile = false, needsLightBacking = false),
        "vllm" to ProviderBrandIconSpec(R.drawable.ccs_vllm, tintable = false, tile = false, needsLightBacking = false),
        "xiaomimimo" to ProviderBrandIconSpec(R.drawable.ccs_xiaomimimo, tintable = false, tile = true, needsLightBacking = false),
        "yylx" to ProviderBrandIconSpec(R.drawable.ccs_yylx, tintable = true, tile = false, needsLightBacking = false),
        "zhipu" to ProviderBrandIconSpec(R.drawable.ccs_zhipu, tintable = false, tile = false, needsLightBacking = false),
    )

    fun resolve(iconKey: String): ProviderBrandIconSpec? {
        val clean = iconKey.trim().lowercase()
        if (clean.isEmpty()) return null
        return specs[clean]
    }
}

/** 供应商品牌图标框：支持 tile 铺满、单色随主题着色、深色底白衬与首字母兜底。 */
@Composable
fun ProviderBrandIconBox(
    icon: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val scheme = MiuixTheme.colorScheme
    val spec = ProviderBrandIcons.resolve(icon)
    val shape = RoundedCornerShape(8.dp)
    val boxBg = when {
        spec == null -> scheme.secondaryContainer
        spec.tile -> Color.Transparent
        spec.needsLightBacking -> Color.White
        else -> scheme.surfaceContainerHigh
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(boxBg)
            .border(0.75.dp, scheme.outline.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (spec != null) {
            Image(
                painter = painterResource(spec.resId),
                contentDescription = name,
                contentScale = if (spec.tile) ContentScale.Crop else ContentScale.Fit,
                colorFilter = if (spec.tintable) ColorFilter.tint(scheme.onBackground) else null,
                modifier = if (spec.tile) Modifier.size(size) else Modifier.size(size * 0.68f),
            )
        } else {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                color = scheme.primary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

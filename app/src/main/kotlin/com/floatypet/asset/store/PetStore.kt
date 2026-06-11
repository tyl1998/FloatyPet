package com.floatypet.asset.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.floatypet.core.model.Pet
import com.floatypet.core.model.PetAction
import com.floatypet.core.model.PetAssetType
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 素材文件读写。目录结构（AGENT.md §1.3）：
 * ```
 * {filesDir}/pet_data/{petId}/
 *   ├─ pet.json
 *   └─ {action}/frame_01.png ...
 * ```
 * 仅本地内部存储，不暴露相册（隐私 CLAUDE.md §8）。用 org.json（Android 内置，零依赖）。
 */
@Singleton
class PetStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File get() = File(context.filesDir, "pet_data").apply { mkdirs() }

    fun petDir(petId: String): File = File(root, petId).apply { mkdirs() }

    private fun actionDir(petId: String, action: PetAction): File =
        File(petDir(petId), action.assetDir).apply { mkdirs() }

    /** 写入某动作的一帧（PNG，保留透明通道）。frameIndex 从 1 开始。 */
    fun writeFrame(petId: String, action: PetAction, frameIndex: Int, bitmap: Bitmap) {
        val name = "frame_%02d.png".format(frameIndex)
        File(actionDir(petId, action), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** 读取某动作的所有帧（按文件名排序）。无帧返回空列表。 */
    fun loadFrames(petId: String, action: PetAction): List<Bitmap> {
        val dir = File(petDir(petId), action.assetDir)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.mapNotNull { BitmapFactory.decodeFile(it.absolutePath) }
            ?: emptyList()
    }

    /** 绝对路径，传给 PetRenderer.loadPet。 */
    fun petAssetPath(petId: String): String = petDir(petId).absolutePath

    fun savePet(pet: Pet) {
        val json = JSONObject().apply {
            put("id", pet.id)
            put("name", pet.name)
            put("assetType", pet.assetType.name)
            put("createdAtMillis", pet.createdAtMillis)
            put("availableActions", JSONArray(pet.availableActions.map { it.name }))
            put("description", pet.description)
        }
        File(petDir(pet.id), "pet.json").writeText(json.toString())
    }

    fun readPet(petId: String): Pet? {
        val f = File(petDir(petId), "pet.json")
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            val actions = o.optJSONArray("availableActions")?.let { arr ->
                buildSet { for (i in 0 until arr.length()) add(PetAction.valueOf(arr.getString(i))) }
            } ?: setOf(PetAction.IDLE)
            Pet(
                id = o.getString("id"),
                name = o.optString("name", "宝贝"),
                assetType = runCatching { PetAssetType.valueOf(o.optString("assetType")) }
                    .getOrDefault(PetAssetType.SPRITE_2D),
                availableActions = actions,
                createdAtMillis = o.optLong("createdAtMillis", 0L),
                description = o.optString("description", ""),
            )
        }.getOrNull()
    }

    fun deletePet(petId: String) {
        petDir(petId).deleteRecursively()
    }

    /** Returns IDs of all pet directories that have a valid pet.json (filesystem discovery). */
    fun listPetIds(): List<String> =
        root.listFiles()
            ?.filter { it.isDirectory && File(it, "pet.json").exists() }
            ?.mapNotNull { dir -> readPet(dir.name)?.id }
            ?: emptyList()
}

package com.floatypet.data

import android.graphics.Bitmap
import com.floatypet.core.model.Pet
import com.floatypet.core.model.PetAction
import kotlinx.coroutines.flow.Flow

/**
 * 宠物数据仓库。当前宠物 id 存 DataStore，素材帧走文件系统（CLAUDE.md §2）。
 */
interface PetRepository {
    /** 当前宠物（无则 null），随保存/删除变化。 */
    val currentPet: Flow<Pet?>

    /** 用一张图片作为宠物的 idle 帧创建/替换当前宠物，返回创建的 Pet。 */
    suspend fun createPetFromImage(name: String, idleFrame: Bitmap): Pet

    /** 读取某动作的帧（供渲染器加载）。 */
    fun framesOf(petId: String, action: PetAction): List<Bitmap>

    /** 素材根路径（传给 PetRenderer.loadPet）。 */
    fun assetPath(petId: String): String
}

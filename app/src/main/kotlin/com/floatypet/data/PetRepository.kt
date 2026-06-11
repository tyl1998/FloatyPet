package com.floatypet.data

import android.graphics.Bitmap
import com.floatypet.core.model.Pet
import com.floatypet.core.model.PetAction
import kotlinx.coroutines.flow.Flow

/**
 * 宠物数据仓库。当前宠物 id 存 DataStore，素材帧走文件系统（CLAUDE.md §2）。
 */
interface PetRepository {
    /** 当前激活的宠物（无则 null），随切换/删除变化。 */
    val currentPet: Flow<Pet?>

    /** 所有宠物列表（按创建时间升序），随添加/删除变化。 */
    val pets: Flow<List<Pet>>

    /** 用一张图片作为宠物的 idle 帧创建新宠物，返回创建的 Pet。不删除其他宠物。 */
    suspend fun createPetFromImage(name: String, idleFrame: Bitmap): Pet

    /** 读取某动作的帧（供渲染器加载）。 */
    fun framesOf(petId: String, action: PetAction): List<Bitmap>

    /** 素材根路径（传给 PetRenderer.loadPet）。 */
    fun assetPath(petId: String): String

    /** AI 路径：frames 已由生成引擎写好，只需把 petId 注册为当前宠物。 */
    suspend fun registerCurrentPet(petId: String)

    /** 切换当前激活宠物。 */
    suspend fun switchPet(petId: String)

    /** 删除一只宠物（帧文件 + 元数据）。若删的是当前宠物则自动切换到最新一只。 */
    suspend fun deletePet(petId: String)
}

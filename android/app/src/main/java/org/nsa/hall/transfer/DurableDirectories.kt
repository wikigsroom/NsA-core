package org.nsa.hall.transfer

import java.io.File
import java.nio.file.Files

/** Directory entries need their own durability barrier, in addition to file data. */
fun durableMkdirs(directory: File, sync: (File) -> Unit) {
    val missing = mutableListOf<File>()
    var current: File? = directory
    while (current != null && !current.exists()) { missing += current; current = current.parentFile }
    val existing = requireNotNull(current)
    require(existing.isDirectory && !Files.isSymbolicLink(existing.toPath()))
    for (item in missing.asReversed()) {
        require(item.mkdir() || item.isDirectory)
        require(!Files.isSymbolicLink(item.toPath()))
        sync(item); sync(item.parentFile!!)
    }
}

/** Reject links before deleting anything; never traverse a different private tree. */
fun deletePrivateTree(file: File) {
    require(!Files.isSymbolicLink(file.toPath()))
    if (!file.exists()) return
    file.walkTopDown().onEnter { require(!Files.isSymbolicLink(it.toPath())); true }
        .forEach { require(!Files.isSymbolicLink(it.toPath())) }
    require(file.deleteRecursively()) { "临时文件清理未完成，请重试" }
}

fun syncDirectoryTree(root: File, sync: (File) -> Unit) {
    require(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
    root.walkBottomUp().onEnter { require(!Files.isSymbolicLink(it.toPath())); true }
        .forEach { file -> require(!Files.isSymbolicLink(file.toPath())); if (file.isDirectory) sync(file) }
}

fun syncAncestors(directory: File, anchor: File, sync: (File) -> Unit) {
    require(directory.canonicalFile.toPath().startsWith(anchor.canonicalFile.toPath()))
    var item = directory
    while (true) { sync(item); if (item == anchor) return; item = item.parentFile ?: error("目录不属于应用存储") }
}

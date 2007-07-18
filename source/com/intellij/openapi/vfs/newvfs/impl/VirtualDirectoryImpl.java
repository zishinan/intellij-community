/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
  private final NewVirtualFileSystem myFS;
  private Object myChildren; // Either HashMap<String, VFile> or VFile[]

  public VirtualDirectoryImpl(final String name, final VirtualDirectoryImpl parent, final NewVirtualFileSystem fs, final int id) {
    super(name, parent, id);
    myFS = fs;
  }

  @NotNull
  public NewVirtualFileSystem getFileSystem() {
    return myFS;
  }

  @Nullable
  private NewVirtualFile findChild(final String name, final boolean createIfNotFound) {
    final NewVirtualFile result = doFindChild(name, createIfNotFound);
    synchronized (this) {
      if (result == null && myChildren instanceof Map) {
        ensureAsMap().put(name, NullVirtualFile.INSTANCE);
      }
    }
    return result;
  }

  @Nullable
  private NewVirtualFile doFindChild(final String name, final boolean createIfNotFound) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      for (VirtualFile file : a) {
        if (namesEqual(name, file.getName())) return (NewVirtualFile)file;
      }

      return createIfNotFound ? createAndFindChildWithEventFire(name) : null;
    }

    final Map<String, VirtualFile> map = ensureAsMap();
    final VirtualFile file = map.get(name);
    if (file == NullVirtualFile.INSTANCE) {
      return createIfNotFound ? createAndFindChildWithEventFire(name) : null;
    }

    if (file != null) return (NewVirtualFile)file;

    int id = ourPersistence.getId(this, name);
    if (id > 0) {
      NewVirtualFile child = createChild(name, id);
      map.put(name, child);
      return child;
    }

    return null;
  }

  public VirtualFileSystemEntry createChild(String name, int id) {
    final VirtualFileSystemEntry child;
    final NewVirtualFileSystem fs = getFileSystem();
    if (ourPersistence.isDirectory(id)) {
      child = new VirtualDirectoryImpl(name, this, fs, id);
    }
    else {
      child = new VirtualFileImpl(name, this, id);
    }

    if (fs.markNewFilesAsDirty()) {
      child.markDirty();
    }

    return child;
  }

  @Nullable
  private NewVirtualFile createAndFindChildWithEventFire(final String name) {
    final NewVirtualFileSystem delegate = getFileSystem();
    VirtualFile fake = new FakeVirtualFile(name, this);
    if (delegate.exists(fake)) {
      VFileCreateEvent event = new VFileCreateEvent(null, this, fake.getName(), delegate.isDirectory(fake), true);
      RefreshQueue.getInstance().processSingleEvent(event);
      return findChild(fake.getName());
    }
    else {
      return null;
    }
  }

  @Nullable
  public NewVirtualFile refreshAndFindChild(final String name) {
    return findChild(name, true);
  }

  @Nullable
  public NewVirtualFile findChildIfCached(final String name) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      for (VirtualFile file : a) {
        if (namesEqual(name, file.getName())) return (NewVirtualFile)file;
      }

      return null;
    }

    final Map<String, VirtualFile> map = asMap();
    if (map != null) {
      final VirtualFile file = map.get(name);
      return file instanceof NewVirtualFile ? (NewVirtualFile)file : null;
    }

    return null;
  }

  @NotNull
  public synchronized VirtualFile[] getChildren() {
    if (myChildren instanceof VirtualFile[]) {
      return (VirtualFile[])myChildren;
    }

    final int[] childrenIds = ourPersistence.listIds(this);
    VirtualFile[] children = new VirtualFile[childrenIds.length];
    final Map<String, VirtualFile> map = asMap();
    for (int i = 0; i < children.length; i++) {
      final int childId = childrenIds[i];
      final String name = ourPersistence.getName(childId);
      VirtualFile child = map != null ? map.get(name) : null;

      children[i] = child != null && child != NullVirtualFile.INSTANCE ? child : createChild(name, childId);
    }

    if (getId() > 0) {
      myChildren = children;
    }

    return children;
  }

  @Nullable
  public NewVirtualFile findChild(@NotNull final String name) {
    return findChild(name, false);
  }

  @Nullable
  private synchronized VirtualFile[] asArray() {
    if (myChildren instanceof VirtualFile[]) return (VirtualFile[])myChildren;
    return null;
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  private synchronized Map<String, VirtualFile> asMap() {
    if (myChildren instanceof Map) return (Map<String, VirtualFile>)myChildren;
    return null;
  }

  @NotNull
  @SuppressWarnings({"unchecked"})
  private synchronized Map<String, VirtualFile> ensureAsMap() {
    assert !(myChildren instanceof VirtualFile[]);

    if (myChildren == null) {
      myChildren = createMap();
    }

    return (Map<String, VirtualFile>)myChildren;
  }

  public synchronized void addChild(VirtualFile file) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      myChildren = ArrayUtil.append(a, file);
    }
    else {
      final Map<String, VirtualFile> m = ensureAsMap();
      m.put(file.getName(), file);
    }
  }

  public synchronized void removeChild(VirtualFile file) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      myChildren = ArrayUtil.remove(a, file);
    }
    else {
      final Map<String, VirtualFile> m = ensureAsMap();
      m.put(file.getName(), NullVirtualFile.INSTANCE);
    }
  }

  public synchronized boolean allChildrenLoaded() {
    return myChildren instanceof VirtualFile[];
  }

  public synchronized List<String> getSuspicousNames() {
    final Map<String, VirtualFile> map = asMap();
    if (map == null) return Collections.emptyList();

    List<String> names = new ArrayList<String>();
    for (String name : map.keySet()) {
      if (map.get(name) == NullVirtualFile.INSTANCE) {
        names.add(name);
      }
    }

    return names;
  }

  public boolean isDirectory() {
    return true;
  }

  @NotNull
  public synchronized Collection<VirtualFile> getCachedChildren() {
    final Map<String, VirtualFile> map = asMap();
    if (map != null) {
      Set<VirtualFile> files = new THashSet<VirtualFile>(map.values());
      files.remove(NullVirtualFile.INSTANCE);
      return files;
    }

    final VirtualFile[] a = asArray();
    if (a != null) return Arrays.asList(a);

    return Collections.emptyList();
  }

  private boolean namesEqual(final String name1, final String name2) {
    return getFileSystem().isCaseSensitive() ? name1.equals(name2) : name1.equalsIgnoreCase(name2);
  }

  private Map<String, VirtualFile> createMap() {
    return getFileSystem().isCaseSensitive()
           ? new THashMap<String, VirtualFile>()
           : new THashMap<String, VirtualFile>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  public synchronized void cleanupCachedChildren() {
    // For tests only!!!
    if (myChildren instanceof Map) {
      myChildren = null;
    }
  }

  public InputStream getInputStream() throws IOException {
    throw new IOException("getInputStream() must not be called against a directory: " + getUrl());
  }

  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, final long newTimeStamp) throws IOException {
    throw new IOException("getOutputStream() must not be called against a directory: " + getUrl());
  }
}
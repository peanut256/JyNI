/*
 * Copyright of JyNI:
 * Copyright (c) 2013, 2014, 2015, 2016, 2017 Stefan Richthofer.
 * All rights reserved.
 *
 *
 * Copyright of Python and Jython:
 * Copyright (c) 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008,
 * 2009, 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017
 * Python Software Foundation.
 * All rights reserved.
 *
 *
 * This file is part of JyNI.
 *
 * JyNI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * JyNI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with JyNI.  If not, see <http://www.gnu.org/licenses/>.
 */


package JyNI;

import JyNI.gc.*;
import org.python.core.*;
import org.python.core.finalization.FinalizeTrigger;
import org.python.modules.gc;
import org.python.modules._weakref.*;
//import org.python.modules.gc.CycleMarkAttr;
// TODO temporary import for static PyFile method
import org.python.core.io.FileIO;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.io.File;
import java.nio.file.FileSystems;

public class JyNI {
	public static final int NATIVE_INT_METHOD_NOT_IMPLEMENTED = -2;

	/**Lazy function call binding.*/
	public static final int RTLD_LAZY = 0x00001;
	/**Immediate function call binding.*/
	public static final int RTLD_NOW = 0x00002;
	/**Mask of binding time value.*/
	public static final int RTLD_BINDING_MASK = 0x3;
	/**Do not load the object.*/
	public static final int RTLD_NOLOAD = 0x00004;
	/**Use deep binding.*/
	public static final int RTLD_DEEPBIND = 0x00008;

	/**If the following bit is set in the MODE argument to `dlopen',
	   the symbols of the loaded object and its dependencies are made
	   visible as if the object were linked directly into the program.*/
	public static final int RTLD_GLOBAL = 0x00100;

	/**Unix98 demands the following flag which is the inverse to RTLD_GLOBAL.
	   The implementation does this by default and so we can define the
	   value to zero.*/
	public static final int RTLD_LOCAL = 0;

	/** Do not delete object when closed.*/
	public static final int RTLD_NODELETE = 0x01000;

	public static final int RTLD_JyNI_DEFAULT = RTLD_LAZY | RTLD_GLOBAL;//RTLD_NOW;

	public static final String DLOPENFLAGS_NAME = "dlopenflags".intern();

	public static final int Py_LT = 0;
	public static final int Py_LE = 1;
	public static final int Py_EQ = 2;
	public static final int Py_NE = 3;
	public static final int Py_GT = 4;
	public static final int Py_GE = 5;

	//Note: nativeHandles keeps (exclusively) natively needed objects save from beeing gc'ed.
	//protected static IdentityHashMap<PyObject, PyObject> nativeHandlesKeepAlive = new IdentityHashMap<>();

	/*
	 * This keeps alive objects (especially CStub-backends) reachable from PyObjects
	 * allocated on the C-stack rather than on the heap.
	 */
	public static HashMap<Long, JyGCHead> nativeStaticPyObjectHeads = new HashMap<>();
	public static Set<Long> JyNICriticalObjectSet = new HashSet<>();

	/*
	 * This is to keep the backend of the native interned string-dict alive.
	 * Todo: Arrange that this is a PyStringMap rather than PyDictionary.
	 * Todo: Join this with nativeStaticPyObjectHeads using a CStub(Simple?)GCHead.
	 */
	public static PyObject nativeInternedStrings = null;
	
	//protected static IdentityHashMap<PyObject, Long> nativeHandles;// = new HashMap<PyObject, Long>();
	//protected static IdentityHashMap<ThreadState, PyException> cur_excLookup;
	/*
	 * Todo: Make this a weak HashMap to allow PyCPeers to be mortal.
	 */
	protected static HashMap<Long, PyObject> CPeerHandles = new HashMap<Long, PyObject>();

	static {
		try {
			//System.out.println("init JyNI.java...");
			/* To make configuration easier, we not only search on the library-path for the libs,
			 * but also on the classpath and in some additional typical places.
			 * We currently don't look inside jar files.
			 */
			String classpath = System.getProperty("java.class.path");
			String libpath = System.getProperty("java.library.path");
//			System.out.println("lp: "+libpath);
			String[] commonPositions = (libpath+File.pathSeparator+classpath).split(File.pathSeparator);
			int idx, pos;
			for (int i = 0; i < commonPositions.length; ++i)
			{
				if (commonPositions[i].endsWith(".jar"))
				{
					idx = commonPositions[i].lastIndexOf(File.separator);
					//System.out.println("com: "+commonPositions[i]);
					if (idx > -1)
						commonPositions[i] = commonPositions[i].substring(0, idx);
					else
						commonPositions[i] = System.getProperty("user.dir");
				}
				//System.out.println(commonPositions[i]);
			}
			//these are relative paths to typical positions where IDEs place their build-results:
			String[] loaderPositions = {".", "bin", "../JyNI-Loader/Release", "../JyNI-Loader/Debug"};
			String[] libPositions = {".", "bin", "../JyNI-C/Release", "../JyNI-C/Debug"};
			String lib = System.mapLibraryName("JyNI");
			boolean loaded;
			String[] fileNames;
			boolean dll = lib.endsWith(".dll");

			String dir = System.getProperty("user.dir");
			//System.out.println("user.dir: "+dir);
			idx = dir.lastIndexOf(File.separator);
			if (idx >= 0) dir = dir.substring(0, idx);
			//else dir = System.getProperty("user.dir");

			if (!dll) {
				// We have to use JyNI-loader on POSIX to customize RTLD-flag.
				String loader = System.mapLibraryName("JyNI-Loader");
				loaded = false;
				fileNames = new String[commonPositions.length+loaderPositions.length];
				pos = 0;
				for (int i = 0; i < commonPositions.length; ++i)
				{
					fileNames[pos++] = commonPositions[i]+File.separator+loader;
				}
				for (int i = 0; i < loaderPositions.length; ++i)
				{
					fileNames[pos++] = dir+File.separator+loaderPositions[i]+File.separator+loader;
				}
				
				
				for (int i = 0; !loaded && i < fileNames.length; ++i)
				{
					File loaderFile = new File(fileNames[i]);
					if (loaderFile.exists())
					{
						System.load(loaderFile.getAbsolutePath());
						loaded = true;
					} //else
						//System.out.println("not found: "+loaderFile.getPath());
				}
				if (!loaded)
				{
					System.err.print("Can't find library file: "+loader);
					System.exit(1);
				}
			}
			
			fileNames = new String[commonPositions.length+libPositions.length];
			pos = 0;
			for (int i = 0; i < commonPositions.length; ++i)
			{
				fileNames[pos++] = commonPositions[i]+File.separator+lib;
			}
			for (int i = 0; i < libPositions.length; ++i)
			{
				fileNames[pos++] = dir+File.separator+libPositions[i]+File.separator+lib;
			}
			
			loaded = false;
			for (int i = 0; !loaded && i < fileNames.length; ++i)
			{
				File libFile = new File(fileNames[i]);
				if (libFile.exists())
				{
					//System.out.println("initJyNI: "+fileNames[i]);
					//nativeHandles = new IdentityHashMap<PyObject, Long>();
					//cur_excLookup = new IdentityHashMap<ThreadState, PyException>(5);
					if (dll) {
						if (!libFile.isDirectory())
						{
							System.err.print("JyNI.dll must be a directory containing actual JyNI.dll as python27.dll: "
									+libFile.getAbsolutePath());
							System.exit(1);
						}
						//System.out.println("loading "+libFile.getAbsolutePath());
						//System.load("D:\\workspace\\linux\\JyNI\\build\\python27.dll");
						System.load(libFile.getAbsolutePath()+File.separator+System.mapLibraryName("python27"));
						initJyNI(null);
					} else {
						initJyNI(libFile.getAbsolutePath());
					}
					//System.out.println("initJyNI done");
					loaded = true;
				}
			}
			if (!loaded)
			{
				System.err.print("Can't find library file: "+lib);
				System.exit(1);
			}
		} catch (Exception ex)
		{
			System.err.println("JyNI: Exception in initializer: "+ex);
		}
	}

	public static native void initJyNI(String JyNILibPath);
	public static native void clearPyCPeer(long objectHandle, long refHandle);
	public static native PyModule loadModule(String moduleName, String modulePath, long tstate);
	public static native PyObject callPyCPeer(long peerHandle, PyObject args, PyObject kw, long tstate);
	public static native PyObject getAttrString(long peerHandle, String name, long tstate);
	public static native int setAttrString(long peerHandle, String name, PyObject value, long tstate);
	public static native PyObject repr(long peerHandle, long tstate);
	public static native String PyObjectAsString(long peerHandle, long tstate);
	public static native PyString PyObjectAsPyString(long peerHandle, long tstate);
	public static native PyObject lookupFromHandle(long handle);
	public static native int currentNativeRefCount(long handle);
	public static native void nativeIncref(long handle, long tstate);
	public static native void nativeDecref(long handle, long tstate);
	public static native String getNativeTypeName(long handle);
	public static native PyObject getItem(long peerHandle, PyObject key, long tstate);
	public static native int setItem(long peerHandle, PyObject key, PyObject value, long tstate);
	public static native int delItem(long peerHandle, PyObject key, long tstate);
	public static native int PyObjectLength(long peerHandle, long tstate);
	public static native PyObject descr_get(long self, PyObject obj, PyObject type, long tstate);
	public static native int descr_set(long self, PyObject obj, PyObject value, long tstate);
	public static native int JyNI_PyObject_Compare(long self, PyObject o, long tstate);
	public static native PyObject JyNI_PyObject_RichCompare(long self, PyObject o, int op, long tstate);
	public static native PyObject JyNI_PyObject_GetIter(long self, long tstate);
	public static native PyObject JyNI_PyIter_Next(long self, long tstate);

	//ThreadState-stuff:
	public static native void setNativeRecursionLimit(int nativeRecursionLimit);
	public static native void setNativeCallDepth(long nativeHandle, int callDepth);
	public static native long initNativeThreadState(JyTState jts, ThreadState ts);
	public static native void clearNativeThreadState(long nativeHandle);

	public static native void JyNIDebugMessage(long mode, long value, String message);

	//List-Stuff:
	public static native PyObject JyList_get(long handle, int index);
	public static native int JyList_size(long handle);
	public static native PyObject JyList_set(long handle, int index, PyObject o, long pyObject);
	public static native void JyList_add(long handle, int index, PyObject o, long pyObject);
	public static native PyObject JyList_remove(long handle, int index);

	//Set-Stuff:
	public static native void JySet_putSize(long handle, int size);

	//PyCFunction-Stuff:
	public static native PyObject PyCFunction_getSelf(long handle, long tstate);
	public static native PyObject PyCFunction_getModule(long handle, long tstate);
	public static native PyObject JyNI_CMethodDef_bind(long handle, PyObject bindTo, long tstate);

	//Number protocol:
	//public static native int JyNI_PyNumber_Check(long o, long tstate);
	public static native PyObject JyNI_PyNumber_Add(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Subtract(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Multiply(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Divide(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Remainder(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Divmod(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Power(long o1, PyObject o2, PyObject o3, long tstate);
	public static native PyObject JyNI_PyNumber_Negative(long o, long tstate);
	public static native PyObject JyNI_PyNumber_Positive(long o, long tstate);
	public static native PyObject JyNI_PyNumber_Absolute(long o, long tstate);
	public static native boolean  JyNI_PyNumber_NonZero(long o, long tstate);
	public static native PyObject JyNI_PyNumber_Invert(long o, long tstate);
	public static native PyObject JyNI_PyNumber_Lshift(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Rshift(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_And(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Xor(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Or(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Coerce(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Int(long o, long tstate);
	public static native PyObject JyNI_PyNumber_Long(long o, long tstate);
	public static native PyObject JyNI_PyNumber_Float(long o, long tstate);
//	public static native PyObject JyNI_PyNumber_Oct(long o, long tstate);
//	public static native PyObject JyNI_PyNumber_Hex(long o, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceAdd(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceSubtract(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceMultiply(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceDivide(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceRemainder(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlacePower(long o1, PyObject o2, PyObject o3, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceLshift(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceRshift(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceAnd(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceXor(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceOr(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_FloorDivide(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_TrueDivide(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceFloorDivide(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_InPlaceTrueDivide(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PyNumber_Index(long o, long tstate);

	public static native int      JyNI_PySequence_Length(long o, long tstate);
	public static native PyObject JyNI_PySequence_Concat(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PySequence_Repeat(long o, int l, long tstate);
	public static native PyObject JyNI_PySequence_GetItem(long o, int l, long tstate); //PySequence_Item
	public static native PyObject JyNI_PySequence_GetSlice(long o, int l1, int l2, long tstate); //PySequence_Slice
	public static native int      JyNI_PySequence_SetItem(long o1, int l, PyObject o2, long tstate); //PySequence_AssItem
	public static native int      JyNI_PySequence_SetSlice(long o, int l1, int l2, PyObject o2, long tstate); //PySequence_AssSlice
	public static native int      JyNI_PySequence_Contains(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PySequence_InPlaceConcat(long o1, PyObject o2, long tstate);
	public static native PyObject JyNI_PySequence_InPlaceRepeat(long o, int l, long tstate);

	public static native int      JyNI_PyMapping_Length(long o, long tstate);
	// use getItem instead of:
	//public static native PyObject JyNI_PyMapping_Subscript(long o1, PyObject o2, long tstate);
	// use setItem instead of:
	//public static native int JyNI_PyMapping_AssSubscript(long o1, PyObject o2, PyObject o3, long tstate);

	// Postpone these until buffer protocol support is focused
//	public static native int      JyNI_PyBuffer_Getreadbuffer(long o, long tstate);
//	public static native int      JyNI_PyBuffer_Getwritebuffer(long o, long tstate);
//	public static native int      JyNI_PyBuffer_Getsegcount(long o, long tstate);
//	public static native int      JyNI_PyBuffer_Getcharbuffer(long o, long tstate);
//	public static native int      JyNI_PyBuffer_Getbuffer(long o, long tstate);
//	public static native void     JyNI_PyBuffer_Releasebuffer(long o, long tstate);

	//ReferenceMonitor- and GC-Stuff:
	public static native void JyRefMonitor_setMemDebugFlags(int flags);

	/**
	 * Returns true, if the whole graph could be deleted (valid graph).
	 * Returns false, if at least one object had to be resurrected (invalid graph).
	 */
	public static native boolean JyGC_clearNativeReferences(long[] nativeRefs, long tstate);

	/**
	 * Note that this method won't acquire the GIL, because it is only called while
	 * JyGC_clearNativeReferences is holding the GIL for it anyway.
	 * JyGC_clearNativeReferences waits (via waitForCStubs) until all CStub-finalizers
	 * are done.
	 */
	public static native void JyGC_restoreCStubBackend(long handle, PyObject backend, JyGCHead newHead);
	//public static native long[] JyGC_validateGCHead(long handle, long[] oldLinks);
	public static native boolean JyGC_validateGCHead(long handle, long[] oldLinks);
	public static native long[] JyGC_nativeTraverse(long handle);
	//protected static native void pinWeakReferent(long handle, long tstate);
	protected static native void releaseWeakReferent(long handle, long tstate);
	//public static native JyGCHead JyGC_lookupGCHead(long handle);

	/* Utility stuff */
	public static native int JyNI_putenv(String value);
	public static native PyObject JyNI_mbcs_encode(PyObject input, PyObject errors, long tstate);
	public static native PyObject JyNI_mbcs_decode(PyObject input, PyObject errors, PyObject fnl, long tstate);


	public static PyObject getPyObjectByName(String name)
	{
		//todo: Check, whether this does what it is supposed to
		//System.out.println("JyNI: Getting Object by name: "+name);
		PySystemState sysState = Py.getSystemState();
		return Py.getThreadState().getSystemState().__dict__.__getitem__(new PyString(name))._doget(sysState);
		//PyObject er = Py.getThreadState().systemState.__dict__.__getitem__(new PyString(name));
		/*PyObject er = sysState.__dict__.__finditem__(name);
		System.out.println("found: "+er.getClass().getName());
		System.out.println(er.getType());
		System.out.println(er.getType().getClass().getName());
		System.out.println(er.getType().getName());
		PyObject er2 = er._doget(sysState);
		System.out.println(er2);
		System.out.println(er2.getClass());
		System.out.println(er2.getType());
		return er;*/
	}

	public static long getJyObjectByName(String name)
	{
		//System.out.println("getJyObjectByName "+name);
		PyObject obj = getPyObjectByName(name);
		//System.out.println("obj: "+obj);
		//Long er = nativeHandles.get(obj);
		//System.out.println("nativeHandles: "+nativeHandles);
		//System.out.println("found: "+er);
		//return er != null ? er : 0;
		return lookupNativeHandle(obj);
	}

	public static void setPyObjectByName(String name, PyObject value)
	{
		//todo: Check, whether this does what it is supposed to
		// System.out.println("JyNI: Setting Object by name: "+name);//+" value: "+value);
		try {
			Py.getThreadState().getSystemState().__setattr__(name.intern(), value);
		} catch (Exception e1)
		{
			try {
				Py.getSystemState().__setattr__(name.intern(), value);
			} catch (Exception e2)
			{
				return;
			}
		}
	}

	public static int getDLVerbose()
	{
		//current hard-coded Debug:
		return 0;
		
		//real implementation:
		/*switch (Options.verbose)
		case Py.DEBUG: return true;
		case Py.COMMENT: return true;
		case Py.MESSAGE: return true;
		default: return false;*/
	}

	public static int getDLOpenFlags()
	{
		try {
			return ((PyInteger) Py.getThreadState().getSystemState().__getattr__(
					DLOPENFLAGS_NAME)).asInt();
		} catch (Exception e1)
		{
			try {
				return ((PyInteger) Py.getSystemState().__getattr__(
						DLOPENFLAGS_NAME)).asInt();
			} catch (Exception e2)
			{
				return RTLD_JyNI_DEFAULT;
			}
		}
	}

	public static void setDLOpenFlags(int n)
	{
		try {
			Py.getThreadState().getSystemState().__setattr__(
					DLOPENFLAGS_NAME, Py.newInteger(n));
		} catch (Exception e1)
		{
			try {
				Py.getSystemState().__setattr__(DLOPENFLAGS_NAME, Py.newInteger(n));
			} catch (Exception e2)
			{
				return;
			}
		}
	}

	public static PyObject sys_getdlopenflags(PyObject[] args, String[] kws)
	{
		try {
			return Py.getThreadState().getSystemState().__getattr__(DLOPENFLAGS_NAME);
		} catch (Exception e1)
		{
			try {
				return Py.getSystemState().__getattr__(DLOPENFLAGS_NAME);
			} catch (Exception e2)
			{
				return Py.newInteger(RTLD_JyNI_DEFAULT);
			}
		}
	}

	public static void sys_setdlopenflags(PyObject[] args, String[] kws)
	{
		if (args.length != 1) throw Py.makeException(Py.TypeError,
			Py.newString("setdlopenflags() takes exactly 1 argument ("+args.length+" given)"));
		if (!(args[0] instanceof PyInteger)) throw Py.makeException(Py.TypeError,
			Py.newString("integer argument expected, got "+args[0].getType().getName()));
		try {
			Py.getThreadState().getSystemState().__setattr__(DLOPENFLAGS_NAME, args[0]);
		} catch (Exception e1)
		{
			try {
				Py.getSystemState().__setattr__(DLOPENFLAGS_NAME, args[0]);
			} catch (Exception e2)
			{
				return;
			}
		}
	}

	public static void registerNativeStaticJyGCHead(long handle, JyGCHead head) {
		nativeStaticPyObjectHeads.put(handle, head);
	}

	public static JyGCHead getNativeStaticJyGCHead(long handle) {
		return nativeStaticPyObjectHeads.get(handle);
	}

	public static void addJyNICriticalObject(long handle) {
		synchronized (JyNICriticalObjectSet) {
			JyNICriticalObjectSet.add(handle);
		}
	}

	public static void removeJyNICriticalObject(long handle) {
		synchronized (JyNICriticalObjectSet) {
			JyNICriticalObjectSet.remove(handle);
		}
	}

	public static int getNativeRefCount(PyObject obj) {
		long handle = lookupNativeHandle(obj);
		if (handle == 0) return -2;
		else return currentNativeRefCount(handle);
	}

	public static void setNativeHandle(PyObject object, long handle) {//, boolean keepAlive) {
		//no WeakReferences needed here, because clearNativeHandle is always called
		//when a corresponding PyObject on C-Side is deallocated

		//todo: When JyNI-gc is stable remove keepAlive mechanism and option here.
		//Was only needed for type-dicts of static types, which don't get JyGCHeads.
		//Now nativeStaticTypeDicts serves this purpose more explicitly.
//		if (keepAlive) {
//			nativeHandlesKeepAlive.put(object, object);
//			//System.out.println("Would keep alive: "+handle+" - "+object);
//		}
//		if (object instanceof PyCPeer) {
//			((PyCPeer) object).objectHandle = handle;
		if (object instanceof CPeerInterface) {
			/* CPeers should have been properly initialized and should keep the same
			 * handle during entire lifetime. Once JyNI is better tested and established
			 * we can remove the following check:
			 */
			if (((CPeerInterface) object).getHandle() != handle)
				System.err.println("JyNI-Warning: CPeerInterface not properly initialized: "+object.getType());
		} else {
			JyAttribute.setAttr(object, JyAttribute.JYNI_HANDLE_ATTR, handle);
		}
	}

	//public static long lookupNativeHandle(PyObject object)
	public static long lookupNativeHandle(PyObject object) {
		if (object == null) return 0;
		//if (object instanceof PyCPeer) return ((PyCPeer) object).objectHandle;
		if (object instanceof CPeerInterface)
			return ((CPeerInterface) object).getHandle();
		else {
			//Long er = nativeHandles.get(object);
			Long er = (Long) JyAttribute.getAttr(object, JyAttribute.JYNI_HANDLE_ATTR);
//			if (er == null && object instanceof PyType) System.out.println("lookup failed: "+((PyType) object).getName());
			return er == null ? 0 : er.longValue();

//			System.out.println("Exception before:");
//			System.out.println(Py.getThreadState().exception);
			//System.out.println("find handle...");
			//PyCPeer peer = (PyCPeer) object.__findattr__(JyNIHandleAttr);
//			System.out.println("Exception afterwards:");
//			System.out.println(Py.getThreadState().exception);
//			System.out.println("retrieved: "+peer);
			//return peer == null ? 0 : ((PyCPeer) peer).objectHandle;
		}
		//Problem: When JyNI is initialized, nativeHandles have not been initialized...
		/*try
		{
			Long er = nativeHandles.get(object);
			return er == null ? 0 : er;
		} catch (Exception e)
		{
			System.out.println("ERRR: "+e);
			//System.out.println(nativeHandles);
			System.out.println(object);
		}
		return 0;*/
	}

	public static PyObject lookupCPeerFromHandle(long handle) {
		//Problem: When JyNI is initialized, nativeHandles have not been initialized...
		if (handle == 0) return null;
		else return CPeerHandles.get(handle);
	}

	public static void clearNativeHandle(PyObject object) {
		if (object == null) {
			System.err.println("JyNI-Warning: clearNativeHandle called with null!");
			return;
		}
		//System.out.println("java clearNativeHandle:");
		//System.out.println(object);
		if (object instanceof PyCPeer)
			((PyCPeer) object).objectHandle = 0;
		else
			JyAttribute.delAttr(object, JyAttribute.JYNI_HANDLE_ATTR);
			//nativeHandles.remove(object);
		//nativeHandlesKeepAlive.remove(object);
			
//		PyCPeer peer = (PyCPeer) object.__findattr__(JyNIHandleAttr);
//		if (peer != null)
//		{
//			peer.objectHandle = 0;
//			object.__delattr__(JyNIHandleAttr);
//		}
	}
	
	public static PyObject _PyImport_FindExtension(String name, String filename) {
		return null;
	}
	
	public static PyObject PyImport_GetModuleDict() {
		return Py.getSystemState().modules;
	}
	
	public static PyObject PyImport_AddModule(String name) {
		String nm = name.intern();
		PySystemState pss = Py.getSystemState();
		PyObject er = pss.modules.__finditem__(nm);
		//if (er != null && er.getType().isSubType(PyModule.TYPE)) return er;
		if (er != null) return er;
		else
		{
			er = new PyModule(nm, new PyNativeRefHoldingStringMap());
			pss.modules.__setitem__(nm, er);
			return er;
		}
	}

	public static PyObject PyImport_ImportModuleNoBlock(String name, boolean top) {
//		System.out.println("PyImport_ImportModuleNoBlock... "+name);
		PyUnicode.checkEncoding(name);
		ReentrantLock importLock = Py.getSystemState().getImportLock();
		if (importLock.tryLock())
		{
//			System.out.println("PyImport_ImportModuleNoBlock acquired lock. Name: "+name);
//			PyObject result = imp.importName(name, top);
//			System.out.println("Result: "+result);
//			importLock.unlock();
//			return result;
			try {
				return imp.importName(name, top);
			} finally {
				importLock.unlock();
			}
		} else {
//			System.out.println("PyImport_ImportModuleNoBlock failed to lock");
			throw Py.ImportError("Failed to import " + name +
					" because the import lock is held by another thread.");
		}
	}

	public static PyObject JyNI_GetModule(String name) {
		String nm = name.intern();
		PySystemState pss = Py.getSystemState();
		PyObject er = pss.modules.__finditem__(nm);
		if (er != null && er.getType().isSubType(PyModule.TYPE)) return er;
		else {
			System.out.println("JyNI: No module found: "+name);
			return null;
		}
	}

	public static PyObject getJythonBuiltins()
	{
		PyFrame fr = Py.getFrame();
		return fr != null ? fr.f_builtins : Py.getSystemState().builtins;
	}

	public static PyObject getJythonLocals() {
		PyFrame fr = Py.getFrame();
		return fr != null ? fr.getLocals() : null;
	}

	public static PyObject getJythonGlobals() {
		PyFrame fr = Py.getFrame();
		return fr != null ? fr.f_globals : null;
	}

	public static PyFrame getJythonFrame() {
		return Py.getFrame();
	}

	public static PyType getPyType(Class pyClass) {
		try {
			Field tp = pyClass.getField("TYPE");
			PyType t = (PyType) tp.get(null);
			//System.out.println(t.getName());
			return t;
		} catch (Exception e) {return null;}
	}

	public static String getTypeNameForNativeConversion(PyObject obj) {
		//JyNI.TestTk$StringVar should be instance
//		String result = obj.getType().getName();
//		System.out.println("getTypeNameForNativeConversion result: "+result);//+" ("+obj.__str__()+")");
//		System.out.println(obj.getClass());
//		System.out.println(obj.getType());
//		System.out.println(obj.getType().getClass());
//		System.out.println(obj.getType().getBase());
//		System.out.println(obj.getType().getBases());
//		PyObject cls = obj.getType().getBases().__getitem__(0);
//		System.out.println(cls);
//		System.out.println(cls.getType());
//		System.out.println(cls.getClass());
		//if (cls instanceof PyClass)
		//System.out.println(((PyType) obj.getType().getBases().__getitem__(0)).getName());
		//if (cls instanceof PyClass)
		return obj.getType().getName();//result;
	}

	public static PyClass getTypeOldStyleParent(PyObject obj) {
		PyObject bases = obj.getType().getBases();
//		System.out.println(obj);
//		System.out.println(bases);
		PyObject result;
		for (int i = 0; i < bases.__len__(); ++i) {
			result = bases.__getitem__(i);
			if (result instanceof PyClass)
				return (PyClass) result;
		}
		return null;
	}

	public static long[] getNativeAvailableKeysAndValues(PyDictionary dict) {
		Map<PyObject, PyObject> map = dict.getMap();
		Iterator<PyObject> it = map.keySet().iterator();
		long l;
		Vector<Long> er = new Vector<Long>();
		while (it.hasNext()) {
			//l = nativeHandles.get(it.next());
			//if (l != null) er.add(l);
			l = lookupNativeHandle(it.next());
			if (l != 0 ) er.add(l);
		}
		it = map.values().iterator();
		while (it.hasNext()) {
			//l = nativeHandles.get(it.next());
			//if (l != null) er.add(l);
			l = lookupNativeHandle(it.next());
			if (l != 0 ) er.add(l);
		}
		long[] er2 = new long[er.size()];
		for (int i = 0; i < er2.length; ++i)
			er2[i] = er.get(i).longValue();
		return er2;
	}
	
	/**
	 * Completes the given argument list argDest. For efficiency, arguments should be allocated
	 * directly as one list. So argDest is expected to already contain the non-keyword arguments.
	 */
	public static String[] prepareKeywordArgs(PyObject[] argsDest, PyDictionary keywords) {
		String[] er = new String[keywords.size()];
		int offset = argsDest.length-er.length, i = 0;
		for (Map.Entry<PyObject, PyObject> entry: keywords.getMap().entrySet()) {
			er[i++] = ((PyString) entry.getKey()).asString();
			argsDest[offset++] = entry.getValue();
		}
		return er;
	}
	
	public static long getCurrentThreadID() {
		return Thread.currentThread().getId();
	}
	
	protected static int lastDictIndex;
	protected static PyDictionary lastDict;
	protected static Iterator<Map.Entry<PyObject, PyObject>> dictIterator;
	public static synchronized JyNIDictNextResult getPyDictionary_Next(PyDictionary dict, int index) {
		if (dict == lastDict && index == lastDictIndex && dictIterator != null) {
			Map.Entry<PyObject, PyObject> me;
			PyObject value;
			while (dictIterator.hasNext()) {
				++lastDictIndex;
				me = dictIterator.next();
				value = me.getValue();
				if (value != null) {
					if (lastDictIndex == dict.size()) lastDictIndex = -lastDictIndex;
					return new JyNIDictNextResult(lastDictIndex, me.getKey(), value);
				}
			}
		} else {
			lastDict = dict;
			dictIterator = dict.getMap().entrySet().iterator();
			lastDictIndex = 0;
			Map.Entry<PyObject, PyObject> me;
			while (lastDictIndex < index) {
				++lastDictIndex;
				if (!dictIterator.hasNext()) return null;
				else dictIterator.next();
			}
			PyObject value;
			while (dictIterator.hasNext()) {
				++lastDictIndex;
				me = dictIterator.next();
				value = me.getValue();
				if (value != null) {
					if (lastDictIndex == dict.size()) lastDictIndex = -lastDictIndex;
					return new JyNIDictNextResult(lastDictIndex, me.getKey(), value);
				}
			}
		}
		return null;
	}
	
	protected static int lastSetIndex;
	protected static BaseSet lastSet;
	protected static Iterator<PyObject> setIterator;
	public static synchronized JyNISetNextResult getPySet_Next(BaseSet set, int index) {
		if (set == lastSet && index == lastSetIndex && setIterator != null) {
			PyObject key;
			while (setIterator.hasNext()) {
				++lastSetIndex;
				key = setIterator.next();
				if (key != null) {
					if (lastSetIndex == set.size()) lastSetIndex = -lastSetIndex;
					return new JyNISetNextResult(lastSetIndex, key);
				}
			}
		} else {
			lastSet = set;
			try {
				Field backend = BaseSet.class.getDeclaredField("_set");
				backend.setAccessible(true);
				Set<PyObject> set2 = (Set<PyObject>) backend.get(set);
				setIterator = set2.iterator();
			} catch (Exception e) {
				lastSet = null;
				setIterator = null;
				return null;
			}
			lastSetIndex = 0;
			while (lastSetIndex < index) {
				++lastSetIndex;
				if (!setIterator.hasNext()) return null;
				else setIterator.next();
			}
			PyObject key;
			while (setIterator.hasNext()) {
				++lastSetIndex;
				key = setIterator.next();
				if (key != null) {
					if (lastSetIndex == set.size()) lastSetIndex = -lastSetIndex;
					return new JyNISetNextResult(lastSetIndex, key);
				}
			}
		}
		return null;
	}
	
	public static synchronized BaseSet copyPySet(BaseSet set) {
		if (set instanceof PySet)
			return new PySet(set);
		else if (set instanceof PyFrozenSet) return set;
		else return null;
	}
	
	public static void printPyLong(PyObject pl) {
		//System.out.println("printPyLong");
		System.out.println(((PyLong) pl).getValue());
	}

	public static long[] lookupNativeHandles(PyList lst) {
		PyObject[] obj = lst.getArray();
		long[] er = new long[obj.length];
		for (int i = 0; i < er.length; ++i)
			er[i] = lookupNativeHandle(obj[i]);
		return er;
	}

	public static void GCTrackPyCPeer(PyCPeer peer) {
		new JyWeakReferenceGC(peer);
	}

	public static PyObjectGCHead makeGCHead(long handle, boolean forMirror, boolean gc) {
//		PyObject obj = lookupFromHandle(handle);
		//System.out.println("makeGCHead for "+obj+" of type "+(obj != null ? obj.getType().getName() : "N/A"));
		//Todo: Use a simpler head if object cannot have links.
		PyObjectGCHead result;
		if (gc) result = forMirror ? new CMirrorGCHead(handle) : new CStubGCHead(handle);
		else result = forMirror ? new CMirrorSimpleGCHead(handle) : new CStubSimpleGCHead(handle);
		new JyWeakReferenceGC(result);
		//System.out.println(result.getClass());
		return result;
	}

	public static JyGCHead makeStaticGCHead(long handle, boolean gc) {
		return gc ? new DefaultTraversableGCHead(handle) : new SimpleGCHead(handle);
	}

	//--------------errors-section-----------------
	protected static PyException JyNI_exc;

	public static PyObject exceptionByName(String name) {
		//System.out.println("look for exception: "+name);
		String rawName = name;
		int pin = name.indexOf('.');
		if (pin != -1) rawName = rawName.substring(pin+1);
		//System.out.println("rawName: "+rawName);
		if (JyNIInitializer.isWindows && rawName.equals("WindowsError")) {
			if (WindowsException.WindowsError == null) {
				System.err.println("JyNI-Warning: Could not obtain WindowsError: "+name);
			}
			return WindowsException.WindowsError;
		}
		try {
			Field exc = Py.class.getField(rawName);
			PyObject er = (PyObject) exc.get(null);
//			System.out.println("return "+er);
//			System.out.println("class: "+er.getClass());
			return er;
		} catch (NoSuchFieldException nsfe) {
			System.err.println("JyNI-Warning: Could not obtain Exception (1): "+name);
			return null;
		} catch (Exception e) {
			System.err.println("JyNI-Warning: Could not obtain Exception (2): "+name);
//			System.err.println("  Reason: "+e);
			return null;
		}
	}

	/*public static void JyErr_SetCurExc(ThreadState tstate, PyObject type, PyObject value, PyTraceback traceback)
	{
		ThreadState tstate0 = tstate == null ? Py.getThreadState() : tstate;
		PyException curexc = cur_excLookup.get(tstate0);
		if (curexc == null)
		{
			curexc = new PyException(type, value, traceback);
			cur_excLookup.put(tstate0, curexc);
		} else
		{
			curexc.type = type;
			curexc.value = value;
			curexc.traceback = traceback;
		}
	}
	
	public static PyException JyErr_GetCurExc(ThreadState tstate)
	{
		ThreadState tstate0 = tstate == null ? Py.getThreadState() : tstate;
		return cur_excLookup.get(tstate0);
	}*/

	protected static PyObject maybeExc(PyObject obj) throws PyException {
		if (obj == null && JyNI_exc != null) {
			PyException tmp_exc = JyNI_exc;
			JyNI_exc = null;
//			System.out.println(tmp_exc);
//			tmp_exc.printStackTrace();
			throw tmp_exc;
		} else return obj;
	}

	protected static boolean maybeExc(boolean bl) throws PyException {
		if (JyNI_exc != null) {
			PyException tmp_exc = JyNI_exc;
			JyNI_exc = null;
			throw tmp_exc;
		} else return bl;
	}

	protected static int maybeExc(int res) throws PyException {
		if (res != 0 && JyNI_exc != null) {
			PyException tmp_exc = JyNI_exc;
			JyNI_exc = null;
			throw tmp_exc;
		} else return res;
	}

	protected static int maybeExc(int res, int err) throws PyException {
		if (res == err && JyNI_exc != null) {
			PyException tmp_exc = JyNI_exc;
			JyNI_exc = null;
			throw tmp_exc;
		} else return res;
	}

	protected static void maybeExc() throws PyException {
		if (JyNI_exc != null) {
			PyException tmp_exc = JyNI_exc;
			JyNI_exc = null;
			throw tmp_exc;
		}
	}

	public static void JyErr_InsertCurExc(ThreadState tstate, PyObject type, PyObject value, PyTraceback traceback) {
//		System.out.println("JyErr_InsertCurExc "+tstate);
//		System.out.println(value);
		if (type == null) type = Py.None;
		if (value == null) value = Py.None;
		JyNI_exc = new PyException(type, value, traceback);
		ThreadState tstate0 = tstate == null ? Py.getThreadState() : tstate;
		tstate0.exception = JyNI_exc;
	}

	public static void JyErr_PrintEx(boolean set_sys_last_vars, ThreadState tstate, PyObject type, PyObject value, PyTraceback traceback) {
		ThreadState tstate0 = tstate == null ? Py.getThreadState() : tstate;
		if (set_sys_last_vars) {
			JyErr_InsertCurExc(tstate0, type, value, traceback);
			tstate0.exception.normalize();
			Py.printException(tstate0.exception);
		} else {
			PyException exc = new PyException(type, value, traceback);
			exc.normalize();
			Py.printException(exc);
		}
	}

	public static void JyErr_DebugPrintEx() {
		ThreadState tstate0 = Py.getThreadState();
		PyException exc = tstate0.exception;
		System.out.println(exc);
		if (exc != null) {
			System.out.println(exc.type);
			System.out.println(exc.value);
		}
	}

	/*public static void PyErr_Restore(PyObject type, PyObject value, PyTraceback traceback) {
		//ThreadState tstate = ;
		//tstate.exception = traceback instanceof PyTraceback ? new PyException(type, value, (PyTraceback) traceback) : new PyException(type, value);
		JyErr_SetCurExc(Py.getThreadState(), type, value, traceback);
	}
	
	public static void PyErr_Clear() {
		//ThreadState tstate = Py.getThreadState();
		//tstate.exception = null;
		cur_excLookup.remove(Py.getThreadState());
	}
	
	public static PyException PyErr_Fetch() {
		//ThreadState tstate = Py.getThreadState();
		//PyException er = tstate.exception;
		PyException er = cur_excLookup.remove(Py.getThreadState());
		//PyErr_Clear();
		return er;
	}
	
	public static PyObject PyErr_Occurred() {
		PyException er = cur_excLookup.get(Py.getThreadState());
		return er == null ? null : er.type;
//		ThreadState tstate = Py.getThreadState();
//		if (tstate.exception != null)
//		{
//			System.out.println("PyErr_Occurred: "+tstate.exception.getMessage());
//			System.out.println(tstate.exception);
//			System.out.println("value "+tstate.exception.value);
//			System.out.println("type "+tstate.exception.type);
//		}
//		return tstate.exception == null ? null : tstate.exception.type;
	}*/

	public static boolean PyErr_ExceptionMatches(PyObject exc, PyObject type, PyObject value, PyTraceback traceback) {
		if (type == null) type = Py.None;
		if (value == null) value = Py.None;
		//PyException cur_exc = cur_excLookup.get(Py.getThreadState());
		//return cur_exc == null ? exc == null : cur_exc.match(exc);
		return (new PyException(type, value, traceback)).match(exc);
//		ThreadState tstate = Py.getThreadState();
//		return tstate.exception == null ? exc == null : tstate.exception.match(exc);
	}

	/*public static void PyErr_SetObject(PyObject exception, PyObject value) {
		PyErr_Restore(exception, value, null);
	}
	
	public static void PyErr_SetString(PyObject exc, String value) {
		PyErr_Restore(exc, Py.newString(value), null);
	}
	
	public static void PyErr_SetNone(PyObject exc) {
		PyErr_Restore(exc, null, null);
	}
	
	public static PyObject PyErr_NoMemory() {
		if (PyErr_ExceptionMatches(Py.MemoryError))
			// already current
			return null;

		// raise the pre-allocated instance if it still exists
//		if (PyExc_MemoryErrorInst)
//			PyErr_SetObject(PyExc_MemoryError, PyExc_MemoryErrorInst);
//		else
//			// this will probably fail since there's no memory and hee,
//			// hee, we have to instantiate this class

		PyErr_SetNone(Py.MemoryError);

		return null;
	}*/

	public static void PyErr_WriteUnraisable(PyObject obj) {
		//Todo: Create and use something like JyNIUnraisableError instead of UnknownError.
		Py.writeUnraisable(new UnknownError("JyNI caused unraisable exception"), obj);
		
		//System.err.println("WriteUnraisableException:");
		//System.err.println(obj);
		//System.err.println(PyErr_Fetch());
		/*PyObject *f, *t, *v, *tb;
		PyErr_Fetch(&t, &v, &tb);
		f = PySys_GetObject("stderr");
		if (f != NULL) {
			PyFile_WriteString("Exception ", f);
			if (t) {
				PyObject* moduleName;
				char* className;
				assert(PyExceptionClass_Check(t));
				className = PyExceptionClass_Name(t);
				if (className != NULL) {
					char *dot = strrchr(className, '.');
					if (dot != NULL)
						className = dot+1;
				}

				moduleName = PyObject_GetAttrString(t, "__module__");
				if (moduleName == NULL)
					PyFile_WriteString("<unknown>", f);
				else {
					char* modstr = PyString_AsString(moduleName);
					if (modstr &&
						strcmp(modstr, "exceptions") != 0)
					{
						PyFile_WriteString(modstr, f);
						PyFile_WriteString(".", f);
					}
				}
				if (className == NULL)
					PyFile_WriteString("<unknown>", f);
				else
					PyFile_WriteString(className, f);
				if (v && v != Py_None) {
					PyFile_WriteString(": ", f);
					PyFile_WriteObject(v, f, 0);
				}
				Py_XDECREF(moduleName);
			}
			PyFile_WriteString(" in ", f);
			PyFile_WriteObject(obj, f, 0);
			PyFile_WriteString(" ignored\n", f);
			PyErr_Clear(); // Just in case
		}
		Py_XDECREF(t);
		Py_XDECREF(v);
		Py_XDECREF(tb);*/
	}

	public static PyTraceback JyNI_PyTraceBack_Here(PyFrame frame, ThreadState tstate) {
		if (tstate == null) tstate = Py.getThreadState();
		if (tstate.exception == null) {
			tstate.exception = new PyException();
		}
		tstate.exception.tracebackHere(frame);
		return tstate.exception.traceback;
	}

	public static int slice_compare(PySlice v, PySlice w) {
		int result = 0;

		//if (v == w)
		if (v.equals(w))
			return 0;

		//if (PyObject_Cmp(v->start, w->start, &result) < 0)
		result = v.start.__cmp__(w.start);
		if (result < 0)
			return -2;
		if (result != 0)
			return result;
		//if (PyObject_Cmp(v->stop, w->stop, &result) < 0)
		result = v.stop.__cmp__(w.stop);
		if (result < 0)
			return -2;
		if (result != 0)
			return result;
		//if (PyObject_Cmp(v->step, w->step, &result) < 0)
		result = v.step.__cmp__(w.step);
		if (result < 0)
			return -2;
		return result;
	}

	public static String JyNI_pyCode_co_code(PyBaseCode code) {
		if (code instanceof PyBytecode) return new String(((PyBytecode) code).co_code);
		else if (code instanceof PyTableCode) return ((PyTableCode) code).co_code;
		else return null;
	}

	public static int JyNI_pyCode_co_flags(PyBaseCode code) {
		return code.co_flags.toBits();
	}

	public static String JyNI_pyCode_co_lnotab(PyBytecode code) {
		return new String(code.co_lnotab);
	}

	public static String getPlatform() {
		return PySystemState.version.asString();
	}

	public static boolean isPosix() {
		return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
	}

	/**
	 * Emulates CPython's way to name sys.platform.
	 */
	public static String getNativePlatform() {
		/* Works according to this table:

		.---------------------.--------.
		| System              | Value  |
		|---------------------|--------|
		| Linux (2.x and 3.x) | linux2 |
		| Windows             | win32  |
		| Windows/Cygwin      | cygwin |
		| Mac OS X            | darwin |
		| OS/2                | os2    |
		| OS/2 EMX            | os2emx |
		| RiscOS              | riscos |
		| AtheOS              | atheos |
		'---------------------'--------'
		*/
		String osname = System.getProperty("os.name");
		if (osname.equals("Linux")) return "linux2";
		if (osname.equals("Mac OS X")) return "darwin";
		// Not considering cygwin for now...
		if (osname.startsWith("Windows")) return "win32";
		return osname.replaceAll("[\\s/]", "").toLowerCase();
	}

	/**
	 * Implements a true putenv by using the native C function.
	 * Entries submitted via this function will be added to the
	 * system's environment set for the current user and process.
	 *
	 * This is needed e.g. for Tkinter to configure the location
	 * of TCL, TK and TIX on Windows, because the backend queries
	 * the corresponding environment variables (TCL_LIBRARY,
	 * TK_LIBRARY, TIX_LIBRARY) from the system rather than by
	 * looking into os.environ. JyNI monkeypatches os.environ to
	 * take this putenv implementation into account.
	 */
	public static int putenv(CharSequence key, CharSequence value) {
		StringBuilder sb = new StringBuilder(key.length()+value.length()+1);
		sb.append(key);
		sb.append('=');
		sb.append(value);
		return JyNI_putenv(sb.toString());
	}

	public static PyObject mbcs_encode(PyObject input, PyObject errors) {
		return JyNI_mbcs_encode(input, errors,
				JyTState.prepareNativeThreadState(Py.getThreadState()));
	}

	public static PyObject mbcs_decode(PyObject input, PyObject errors, PyObject fnl) {
		return JyNI_mbcs_decode(input, errors, fnl,
				JyTState.prepareNativeThreadState(Py.getThreadState()));
	}

	public static int PyFile_fd(PyObject fileo) {
		PyFile file = (PyFile)fileo;
		Object fileno = file.fileno().__tojava__(FileIO.class);
		if (!(fileno instanceof FileIO))
			System.out.println("Warning: JyNI will crash now because fileno is no FileIO");
		return ((PyInteger)((FileIO)fileno).__int__()).getValue();
	}

	/**
	 * a variant of the builtin type() constructor producing a type
	 * backed by old-style class.
	 */
	public static PyObject oldStyle_type(PyObject name, PyObject bases, PyObject dict) {
		return PyClass.classobj___new__(name, bases, dict);
	}

	public static boolean isLibraryAvailable(String libname) {
		if (JyNIInitializer.importer == null) return false;
		if (JyNIImporter.dynModules.containsKey(libname))
			// Must be checked here to account for statically linked libs
			return true;
		String suf = "."+JyNIImporter.getSystemDependentDynamicLibraryExtension();
		for (String s : JyNIInitializer.importer.libPaths)
		{
			File fl = new File(s);
			String[] ch = fl.list();
			if (ch != null)
			{
				for (String m : ch)
				{
					if (m.startsWith(libname+".") && m.endsWith(suf))
						return true;
				}
			}
		}
		return false;
	}

	public static boolean isLibraryBuiltin(String libname) {
		if (JyNIInitializer.importer == null) return false;
		if (JyNIImporter.dynModules.containsKey(libname))
			return JyNIImporter.dynModules.get(libname).path == null;
		return JyNIImporter.builtinlist.contains(libname);
	}

	public static boolean isLibraryFileAvailable(String libname) {
		if (JyNIInitializer.importer == null) return false;
		if (JyNIImporter.dynModules.containsKey(libname))
			// Must be checked here to account for statically linked libs
			return JyNIImporter.dynModules.get(libname).path != null;
		String suf = "."+JyNIImporter.getSystemDependentDynamicLibraryExtension();
		for (String s : JyNIInitializer.importer.libPaths)
		{
			File fl = new File(s);
			String[] ch = fl.list();
			if (ch != null)
			{
				for (String m : ch)
				{
					if (m.startsWith(libname+".") && m.endsWith(suf))
						return true;
				}
			}
		}
		return false;
	}

	public static void jPrint(String msg) {
		if (msg == null) System.out.println("null (actually)");
		else System.out.println(msg);
		//System.out.flush();
	}
	
	public static void jPrint(long val) {
		System.out.println(val);
		//System.out.flush();
	}

	public static int jGetHash(Object val) {
		return System.identityHashCode(val);
//		try {
//			System.out.println(val.hashCode()+" ("+System.identityHashCode(val)+")");
//		} catch (Exception e) {
//			System.out.println("("+System.identityHashCode(val)+")");
//		}
		//System.out.flush();
	}

	public static void jPrintInfo(Object val) {
		System.out.println("Object: "+val);
		System.out.println("Class: "+val.getClass());
		//System.out.flush();
	}

//---------gc-section-----------
	/*
	 * Stuff here is actually no public API, but partly
	 * declared public for interaction with JyNI.gc-package.
	 * Todo: Maybe move it to JyNI.-package and make it
	 *   protected or package-scoped.
	 */
	public static final int JYNI_GC_CONFIRMED_FLAG = 1;
	public static final int JYNI_GC_RESURRECTION_FLAG = 2;
	public static final int JYNI_GC_LAST_CONFIRMATION_FLAG = 4;
	public static final int JYNI_GC_MAYBE_RESURRECT_FLAG = 8;
	//public static final int JYNI_GC_HANDLE_NO_CONFIRMATIONS = -1;

	//Confirm deletion of C-Stubs:
	static long[] confirmedDeletions, resurrections;
	static int confirmationsUnconsumed = 0;
	static Set<Long> unconsumedTracker = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
	static Map<Long, ResurrectableGCHead> preconsumedMaybeResurrect = new HashMap<>();

	/**
	 * Checks if JyNI's GC is in a proper workable state. We added this
	 * method, because we had some bugs that resulted in a silent deadlock
	 * of GC threads. This function aims to assert that GC-mechanism is in
	 * a sane state.
	 * Note that this method might block for a moment if it is called during
	 * a GC run in progress. The timeout parameter ensures that it wouldn't
	 * block forever if GC is deadlocked.
	 * 
	 * @return true if JyNI's GC is in a proper workable state.
	 */
	public static boolean isGCSane(int timeout_seconds) {
		long timestamp = System.currentTimeMillis();
		while (!JyWeakReferenceGC.isWaitingOnRefQueue()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {}
			if (System.currentTimeMillis()-timestamp > timeout_seconds * 1000)
				return false;
		}
		return true;
	}

	public static int getPreconsumedSize() {
		return preconsumedMaybeResurrect.size();
	}

	private static void gcDeletionReport(long[] confirmed, long[] resurrected) {
		boolean postProcess = false;
		int preconsumed = 0;
		if (confirmed != null) {
			for (long l: confirmed) {
				if (preconsumedMaybeResurrect.containsKey(l)) {
					++preconsumed;
					preconsumedMaybeResurrect.remove(l);
				} else
					unconsumedTracker.add(l);
			}
		}
		if (resurrected != null) {
			for (long l: resurrected) {
				if (preconsumedMaybeResurrect.containsKey(l)) {
					++preconsumed;
					resurrect(l, preconsumedMaybeResurrect.remove(l));
				} else
					unconsumedTracker.add(l);
			}
		}
		//CStubGCHead.class.notify();

		//some debug-info:
//		System.out.println("gcDeletionReport");
//		if (confirmed != null) {
//			System.out.println("confirmed cstub deletions:");
//			for (int i = 0; i < confirmed.length; ++i) {
//				System.out.println("  "+confirmed[i]);
//			}
//		} else System.out.println("no confirmed cstub deletions");
//		if (resurrected != null) {
//			System.out.println("resurrections:");
//			for (int i = 0; i < resurrected.length; ++i) {
//				System.out.println("  "+resurrected[i]);
//			}
//		} else System.out.println("no cstub resurretions");
		
		synchronized (CStubGCHead.class) {
			if (confirmationsUnconsumed > 0) {
				/*
				 * This might happen if gc-runs overlap (i.e. a gc-run overlaps the
				 * finalization-phase of the previous one - depending on JVM implementation
				 * details this can happen or not), which should not (cannot?) happen in
				 * usual operation, but maybe one can provoke it by calling System.gc in
				 * high frequency. Just in case - maybe we just wait a bit if this
				 * occurs (but release the monitor to let CStub-finalizers catch up).
				 * Or it is due to a JyNI-bug. Both cases should be investigated, so we
				 * notify the user:
				 */
				System.err.println("JyNI-warning: There are unconsumed gc-confirmations!");
//				for (long l: unconsumedTracker) {
//					System.err.println(l+" - "+JyWeakReferenceGC.refTNList.get(l));
//				}
				while (confirmationsUnconsumed > 0) {
					try {
						CStubGCHead.class.wait();
					} catch (InterruptedException ie) {}
				}
			}
			confirmedDeletions = confirmed;
			resurrections = resurrected;
			confirmationsUnconsumed =
					(confirmedDeletions == null ? 0 : confirmedDeletions.length) +
					(resurrections == null ? 0 : resurrections.length) - preconsumed;
			if (confirmationsUnconsumed == 0)
				postProcess = true;
			else
				CStubGCHead.class.notify();
		}
		//System.out.println("unconsumed: "+confirmationsUnconsumed);
		if (postProcess) postProcessCStubGCCycle();
	}

	/**
	 * Do not call this method, it is internal API.
	 */
	public static int consumeConfirmation(long handle, ResurrectableGCHead head) {
		synchronized (CStubGCHead.class) {
			while (confirmationsUnconsumed == 0) {
				try {
					//System.out.println("consumeConfirmation waiting... "+handle);
					CStubGCHead.class.wait();
				} catch(InterruptedException ie) {}
			}
			if (!unconsumedTracker.contains(handle)) {
				//System.out.println("preconsume "+handle);
				preconsumedMaybeResurrect.put(handle, head);
				return JYNI_GC_MAYBE_RESURRECT_FLAG;
			} else {
				unconsumedTracker.remove(handle);
				//System.out.println("consumeConfirmation resume "+handle);
				int i;
				if (confirmedDeletions != null) {
					for (i = 0; i < confirmedDeletions.length; ++i) {
						if (confirmedDeletions[i] == handle) {
							int result = --confirmationsUnconsumed != 0 ?
									JYNI_GC_CONFIRMED_FLAG :
									(JYNI_GC_CONFIRMED_FLAG | JYNI_GC_LAST_CONFIRMATION_FLAG);
							//System.out.println("consumeConfirmation "+handle+" is deletion");
							CStubGCHead.class.notify();
							return result;
						}
					}
				}
				if (resurrections != null) {
					for (i = 0; i < resurrections.length; ++i) {
						if (resurrections[i] == handle) {
							CStubGCHead.class.notify();
							int result = --confirmationsUnconsumed != 0 ?
									(JYNI_GC_RESURRECTION_FLAG) :
									(JYNI_GC_RESURRECTION_FLAG | JYNI_GC_LAST_CONFIRMATION_FLAG);
							//System.out.println("consumedConfirmation "+handle+" is resurrection");
							CStubGCHead.class.notify();
							return result;
						}
					}
				}
				System.err.println("Fatal JyNI GC error: Inconsistent tracker behavior!");
			}
			//System.out.println("consumeConfirmation "+handle+" not consumed anything");
			return 0;
		}
	}

	public static void waitForCStubs() {
		//System.out.println("maybe waitForCStubs...");
		synchronized (CStubGCHead.class) {
			while (confirmationsUnconsumed > 0) {
				try {
					//System.out.println("waitForCStubs "+confirmationsUnconsumed+" "+Thread.currentThread().getName());
					CStubGCHead.class.wait(); //JyNI-GCRefReaper thread hanging here
				} catch(InterruptedException ie) {}
			}
			//System.out.println("waitForCStubs done");
		}
	}

//	public static void maybeResurrect(long handle, ResurrectableGCHead head) {
//		System.out.println("maybeResurrect "+ handle);
//		preconsumedMaybeResurrect.put(handle, head);
//	}

	// To make sure that resurrected objects cannot be collected again within same cycle.
	protected static List<JyGCHead> resurrectionQueue = new ArrayList<>(200);
	public static void resurrect(long handle, ResurrectableGCHead head) {
		ResurrectableGCHead newHead = head.makeResurrectedHead();
		PyObject object = head.getPyObject();
		JyGC_restoreCStubBackend(handle, object, newHead);
		resurrectionQueue.add(newHead);
		new JyWeakReferenceGC(newHead);
		//System.out.println("Resurrect CStub "+object);
		CStubRestoreAllReachables(object);
		JyReferenceMonitor.notifyResurrect(handle, object);
	}

	protected static class visitRestoreCStubReachables implements Visitproc {
		static visitRestoreCStubReachables defaultInstance
				= new visitRestoreCStubReachables();

		//Set<PyObject> alreadyExplored = new HashSet<>();
		IdentityHashMap<PyObject, PyObject> alreadyExplored = new IdentityHashMap<>();
		public Stack<PyObject> explorationStack = new Stack<>();
		
		public static void clear() {
			defaultInstance.alreadyExplored.clear();
		}

		@Override
		public int visit(PyObject object, Object arg) {
			//if (alreadyExplored.contains(object))
			if (alreadyExplored.containsKey(object))
				return 0;
			if (continueCStubExplore(object)) {
				explorationStack.push(object);
			}
			alreadyExplored.put(object, object);
			CStubReachableRestore(object);
			return 0;
		}
	}

	protected static boolean continueCStubExplore(PyObject obj) {
		if (obj instanceof CPeerInterface && JyNICriticalObjectSet.contains(
				((CPeerInterface) obj).getHandle())) return false;
		if (!gc.isTraversable(obj)) return false;
		long handle = lookupNativeHandle(obj);
		JyWeakReferenceGC headRef = JyWeakReferenceGC.lookupJyGCHead(handle);
		if (headRef == null) return true;
		JyGCHead head = headRef.get();
		return head == null || !(head instanceof CStubGCHead || head instanceof CStubSimpleGCHead);
	}

	protected static void CStubReachableRestore(PyObject obj) {
		//System.out.println("Restore: "+obj);
		gc.abortDelayedFinalization(obj);
		//gc.restoreFinalizer(obj); (done in abortDelayedFinalization)
		gc.restoreWeakReferences(obj);
	}

	/**
	 * Do not call this method, it is internal API.
	 */
	public static void CStubRestoreAllReachables(PyObject CStubBackend) {
		CStubReachableRestore(CStubBackend);
		if (gc.isTraversable(CStubBackend)) {
			gc.traverse(CStubBackend, visitRestoreCStubReachables.defaultInstance, null);
			while (!visitRestoreCStubReachables.defaultInstance.explorationStack.empty()) {
				gc.traverse(visitRestoreCStubReachables.defaultInstance.explorationStack.pop(),
						visitRestoreCStubReachables.defaultInstance, null);
			}
		}
	}

	/**
	 * Do not call this method, it is internal API.
	 */
	public static void postProcessCStubGCCycle() {
		/*
		 * Note: This method is *not* triggered by gc-module postFinalization process.
		 * Instead we know (do we?) from JyWeakReferenceGC how many CStub-finalizers
		 * are expected.
		 */
		visitRestoreCStubReachables.clear();
		// Todo: Better keep it for a while to avoid frequent resurrection of the same objects.
		// E.g. use SoftReference or clear it partly or use some generation management.
		resurrectionQueue.clear();

		// Clearing preconsumedMaybeResurrect here can break GC if GC is called
		// subsequently without delay. Todo: Secure this somehow via generation
		// management or SoftReferences.
		preconsumedMaybeResurrect.clear();

		//Should be done automatically:
		//GlobalRef.processDelayedCallbacks();
		//gc.notifyPostFinalization(); (maybe include this later)
	}

	/**
	 * Do not call this method, it is internal API.
	 */
	public static void preProcessCStubGCCycle() {
		/* We pretend to be another finalizer here ending in postProcessCStubGCCycle().
		 * We can do that, because we know when the last CStub finalizer is processed.
		 */
		//gc.notifyPreFinalization(); (maybe include this later)
		gc.removeJythonGCFlags(gc.FORCE_DELAYED_FINALIZATION);
		//Here we care to repair the referenceGraph and to restore weak references.
//		System.out.println("preProcessCStubGCCycle");
		JyWeakReferenceGC headRef;
		JyGCHead head;
		boolean delayFinalization = false;
		long[] criticalHandles;
		synchronized (JyNICriticalObjectSet) {
			/* We cache current handles in synchronized manner before the actual operation
			 * to avoid concurrent modification exception.
			 */
			criticalHandles = new long[JyNICriticalObjectSet.size()];
			int i = 0;
			for (long handle: JyNICriticalObjectSet)
				criticalHandles[i++] = handle;
		}
		for (long handle: criticalHandles) {
			headRef = JyWeakReferenceGC.lookupJyGCHead(handle);
			/*
			 * Evaluate JyGC_validateGCHead first, since it has the side-effect to
			 * update the JyNI-critical's GCHead if necessary. This is also the reason
			 * why we cannot break this loop on early success.
			 */
			if (headRef != null) {
				head = headRef.get();
				if (head != null && head instanceof TraversableGCHead) {
					delayFinalization = JyGC_validateGCHead(handle,
							((TraversableGCHead) head).toHandleArray()) || delayFinalization;
				} else if (head != null)
					System.err.println(
							"JyNI-error: Encountered JyNI-critical with non-traversable JyGCHead! "
							+headRef.getNativeRef());
			}
		}
		if (delayFinalization) {
			//enable delayed finalization in GC-module
			//System.out.println("Force delayed finalization...");
			gc.addJythonGCFlags(gc.FORCE_DELAYED_FINALIZATION);
		}
//		System.out.println("preProcessCStubGCCycle done");
	}

	protected static void suspendPyInstanceFinalizer(PyInstance inst) {
		FinalizeTrigger ft =
				(FinalizeTrigger) JyAttribute.getAttr(inst, JyAttribute.FINALIZE_TRIGGER_ATTR);
		if (ft != null) ft.clear();
	}

	protected static void restorePyInstanceFinalizer(PyInstance inst) {
		FinalizeTrigger ft =
				(FinalizeTrigger) JyAttribute.getAttr(inst, JyAttribute.FINALIZE_TRIGGER_ATTR);
		if (ft != null && (ft.flags & FinalizeTrigger.FINALIZED_FLAG) == 0) ft.trigger(inst);
		else gc.restoreFinalizer(inst);
	}

//---------------Weak Reference section-------------------
	
	protected static ReferenceType createWeakReferenceFromNative(PyObject referent, long handle, PyObject callback) {
//		System.out.println("createWeakReferenceFromNative "+handle);
		if (referent == null)
			return new ReferenceType(JyNIEmptyGlobalReference.defaultInstance, callback);
		ReferenceBackend gref = GlobalRef.newInstance(referent);
		((JyNIGlobalRef) gref).initNativeHandle(handle);
		return new ReferenceType(gref, callback); //Todo: support callback
	}

	protected static ProxyType createProxyFromNative(PyObject referent, long handle, PyObject callback) {
//		System.out.println("createProxyFromNative "+handle);
		if (referent == null)
			return new ProxyType(JyNIEmptyGlobalReference.defaultInstance, callback);
		ReferenceBackend gref = GlobalRef.newInstance(referent);
		((JyNIGlobalRef) gref).initNativeHandle(handle);
		return new ProxyType(gref, callback); //Todo: support callback
	}

	protected static CallableProxyType createCallableProxyFromNative(PyObject referent, long handle, PyObject callback) {
		//System.out.println("createCallableProxyFromNative "+handle);
		if (referent == null)
			return new CallableProxyType(JyNIEmptyGlobalReference.defaultInstance, callback);
		ReferenceBackend gref = GlobalRef.newInstance(referent);
		((JyNIGlobalRef) gref).initNativeHandle(handle);
		return new CallableProxyType(gref, callback); //Todo: support callback
	}

	protected static ReferenceBackend getGlobalRef(PyObject obj) {
		return (ReferenceBackend) JyAttribute.getAttr(obj, JyAttribute.WEAK_REF_ATTR);
//		Object result = JyAttribute.getAttr(obj, JyAttribute.WEAK_REF_ATTR);
//		if (result != null && result instanceof GlobalRef)
//			return (GlobalRef) result;
//		else
//			return null;
	}

	public static void listGCLinks(PyObject obj) {
		System.out.println("GC-Links of "+System.identityHashCode(obj));
		if (obj instanceof TraversableGCHead) {
			((TraversableGCHead) obj).printLinks(System.out);
		} else System.out.println("not a TraversableGCHead");
		System.out.println();
	}
}

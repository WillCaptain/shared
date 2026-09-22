package org.twelve.aipp.host;

/** Provider-neutral contract for embedding an Anna runtime in an AIPP widget. */
public interface AnnaRuntimeHostInterfaceSpec {
    String INTERFACE_TYPE = "shared.anna.runtime/v1";

    String OP_MOUNT = "mount";
    String OP_UPDATE = "update";
    String OP_UNMOUNT = "unmount";
    String OP_COMMAND = "command";

    String COMMAND_FIT = "fit";
    String COMMAND_ZOOM_IN = "zoom-in";
    String COMMAND_ZOOM_OUT = "zoom-out";
}

package io.swerr.multisession.session;

public interface PackSourceAccess {

    PackState multisession$installedPacks();

    PackState multisession$newPacks();

    void multisession$installPacks(PackState state);

    boolean multisession$keepLoadedPacks(PackState state);
}

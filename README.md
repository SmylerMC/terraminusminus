<div align="center">
    <h1>TerraMinusMinus</h1>
    <img alt="GitHub License" src="https://img.shields.io/github/license/SmylerMC/terraminusminus?style=flat-square">
    <img alt="GitHub Workflow Status" src="https://img.shields.io/github/actions/workflow/status/SmylerMC/terraminusminus/test.yml?style=flat-square">
</div>



## What is it?

Terra-- is a fork of Terra++ intended to strip down dependencies to Minecraft and Forge so it can be used safely in as dependency for other projects (this is not a mod).

## How to use ?

### :warning: This project is still experimental

Just add the required maven repositories to your `build.gradle`, and declare Terraminusminus as a dependency.

I.e.:
```groovy
repositories {

    // Smyler's repository has the Terraminusminus builds
    maven {
        name = "Smyler Snapshots"
        url = "https://maven.smyler.net/snapshots/"
    }
    
    // Classic JCenter repository that has most of what we need
    jcenter()
    
    // JitPack is required to build some dependencies
    maven {
        name = "JitPack"
        url = "https://jitpack.io/"
    }
    
    // DaPorchop's repo for PorkLib
    maven {
        name = "DaPorkchop_"
        url = "https://maven.daporkchop.net/"
    }
    
    // This is for leveldb
    maven {
        name = "OpenCollab Snapshots"
        url = "https://repo.opencollab.dev/snapshot/"
    }
    
}

dependencies {

    // Include this repository as a dependency.
    // master-SNAPSHOT indicates to use the last commit built from master,
    // you can replace this with  a reference to another branch 
    compile 'net.buildtheart:terraminusminus:master-SNAPSHOT'
    
    // Your other dependencies would go down there...
}
```

### Release channels

At this point, only branch snapshots are available.
You can browse the available builds at [maven.smyler.net](https://maven.smyler.net/#/snapshots/net/buildtheearth/terraminusminus).

## APIs:

- Tree cover data: [treecover2000 v1.7](https://earthenginepartners.appspot.com/science-2013-global-forest/download_v1.7.html) hosted by [@DaPorkchop_](https://github.com/DaMatrix)
- Building+Road+Water data: [OpenStreetMap](https://www.openstreetmap.org/) under the [Open Database License](https://www.openstreetmap.org/copyright). It is downloaded in real-time using [TerraPlusPlusOSMTileGen](https://github.com/DaMatrix/TerraPlusPlusOSMTileGen) hosted by [@DaPorkchop_](https://github.com/DaMatrix). (© OpenStreetMap contributors)

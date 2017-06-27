# Gephi Filter Helper
This program uses [Gephi Toolkit](https://gephi.org/toolkit/) which provides essential [Gephi](https://gephi.org) modules e.g. Filters, Layout. <br/>
It reads from a user defined configuration file consists of a sequence of tasks and automatically apply them in a chain. <br/>
In this way it solves the problem that visulization of a graph takes too much memory.  <br/>
By processing a graph without visualizing it, it is able to handle graphs hundreds of times bigger on the same machine.

## Design Principle
Three Layers:
- Parses configurations in file <br/>
- Package a task and its arguments and pass to executor <br/>
- The executor calls corresponding handling method using Java Reflection <br/>

## Configuration File
Fromat example refer to [graph.config](https://github.com/researchgraph/Gephi-Filter/blob/master/graph.config) <br/>
Detail parameters of defined tasks see section below.

## Implemented Methods

### Filter
#### GiantComponentsFilter
arguments: N/A

#### DegreeFilter
arguments: lowerBound, upperBound

#### InDegreeFilter
arguments: lowerBound, upperBound

#### NeighborNetworkFilter
arguments: depth

<br/>

### Layout
#### RotateLayout
arguments: degree

#### ScaleLayout
arguments: scaleFactor

#### ForceAtlasLayout
arguments: iterations (optional)

#### ForceAtlas2Layout
arguments: iterations (optional)

#### OpenOrdLayout
arguments: N/A

#### YifanHuLayout
arguments: iterations (optional)

#### RandomLayout
arguments: spaceSize (optional)

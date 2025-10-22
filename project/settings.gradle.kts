rootProject.name = "sta"

include("schemas")
include("producer")
include("lakehouse")                        
include("lakehouse:streaming")              
include("lakehouse:schema-management")      
include("lakehouse:bronze")                 
include("lakehouse:silver")                 
include("lakehouse:gold")                   

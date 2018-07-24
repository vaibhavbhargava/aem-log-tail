 $(function() {
     $.get("/bin/accesslog.lognames.json")
     .done(function(data) {
         $.each(data, function(i, item) {
             $("#lognames").append(' <coral-select-item value="' + item.value + '">' + item.text + '</coral-select-item>');
         });
         setTimeout(
             function() {
                 $("#lognameLoadAlert").hide();

             }, 1000);

     });

     $("#getLogs").click(function() {
        $("#getLogsInfo").show();
        

        $.get("/bin/accesslog.html?logname=" + $("input[name=logname]").val() + "&datasize=" + $("#datasize").val())
        .done(function(data) {
            $("#logContainer").html(data);
            $("#logContainer").show();
            setTimeout(
                function() {
                    $("#getLogsInfo").hide();
                }, 1000);
            
        });
	});
 });

